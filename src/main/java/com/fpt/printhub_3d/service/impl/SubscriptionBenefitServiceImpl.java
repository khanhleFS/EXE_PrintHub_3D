package com.fpt.printhub_3d.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fpt.printhub_3d.dto.subscription.BenefitPreviewResponseDTO;
import com.fpt.printhub_3d.entity.SubscriptionPlan;
import com.fpt.printhub_3d.entity.SubscriptionUsageLog;
import com.fpt.printhub_3d.entity.User;
import com.fpt.printhub_3d.entity.UserSubscription;
import com.fpt.printhub_3d.repository.SubscriptionUsageLogRepository;
import com.fpt.printhub_3d.repository.UserSubscriptionRepository;
import com.fpt.printhub_3d.service.SubscriptionBenefitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionBenefitServiceImpl implements SubscriptionBenefitService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final UserSubscriptionRepository userSubscriptionRepository;
    private final SubscriptionUsageLogRepository usageLogRepository;
    private final ObjectMapper objectMapper;

    private record ParsedDiscount(
            int percent,
            List<String> appliesTo,
            BigDecimal minEligibleSubtotal,
            BigDecimal maxDiscountAmount
    ) {}

    private record ParsedShipping(
            Integer freeUsesPerTerm,
            BigDecimal maxDiscountAmountPerUse,
            List<String> appliesTo
    ) {}

    private record ParsedProduction(
            boolean priorityEnabled,
            List<String> appliesTo
    ) {}

    private record ParsedBenefits(
            ParsedDiscount discount,
            ParsedShipping shipping,
            ParsedProduction production
    ) {}

    private record EvaluationResult(
            boolean hasActiveSubscription,
            UserSubscription primarySub,
            UserSubscription discountSub,
            int discountPercent,
            BigDecimal discountAmount,
            UserSubscription shippingSub,
            BigDecimal shippingDiscountAmount,
            Integer freeShippingUsesLimit,
            int freeShippingUsesUsed,
            Integer remainingFreeShippingUses,
            UserSubscription prioritySub,
            boolean priorityProduction,
            BigDecimal itemsSubtotal,
            BigDecimal baseShippingFee,
            BigDecimal finalShippingFee,
            BigDecimal finalTotal,
            String ineligibleReason
    ) {}

    @Override
    @Transactional(readOnly = true)
    public BenefitPreviewResponseDTO previewBenefits(
            User user,
            BenefitScope scope,
            BigDecimal itemsSubtotal,
            BigDecimal baseShippingFee
    ) {
        return previewBenefits(user, scope, itemsSubtotal, baseShippingFee, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public BenefitPreviewResponseDTO previewBenefits(
            User user,
            BenefitScope scope,
            BigDecimal itemsSubtotal,
            BigDecimal baseShippingFee,
            BigDecimal customSubtotal,
            BigDecimal marketplaceSubtotal
    ) {
        BenefitScope safeScope = scope != null ? scope : BenefitScope.MARKETPLACE;
        List<UserSubscription> activeSubs = user != null
                ? safeList(userSubscriptionRepository.findActiveSubscriptionsByUser(user))
                : List.of();

        EvaluationResult eval = evaluate(
                activeSubs,
                safeScope,
                itemsSubtotal,
                baseShippingFee,
                customSubtotal,
                marketplaceSubtotal
        );

        UserSubscription mainSub = resolveMainSubscription(eval);
        return BenefitPreviewResponseDTO.builder()
                .hasActiveSubscription(eval.hasActiveSubscription())
                .userSubscriptionId(mainSub != null ? mainSub.getId() : null)
                .planName(mainSub != null && mainSub.getPlan() != null ? mainSub.getPlan().getName() : null)
                .appliedDiscountPlanName(eval.discountSub() != null && eval.discountSub().getPlan() != null
                        ? eval.discountSub().getPlan().getName() : null)
                .appliedShippingPlanName(eval.shippingSub() != null && eval.shippingSub().getPlan() != null
                        ? eval.shippingSub().getPlan().getName() : null)
                .discountPercent(eval.discountPercent())
                .itemsSubtotal(eval.itemsSubtotal())
                .discountAmount(eval.discountAmount())
                .baseShippingFee(eval.baseShippingFee())
                .shippingDiscountAmount(eval.shippingDiscountAmount())
                .finalShippingFee(eval.finalShippingFee())
                .freeShippingUsesLimit(eval.freeShippingUsesLimit())
                .freeShippingUsesUsed(eval.freeShippingUsesUsed())
                .remainingFreeShippingUses(eval.remainingFreeShippingUses())
                .priorityProduction(eval.priorityProduction())
                .finalTotal(eval.finalTotal())
                .ineligibleReason(eval.ineligibleReason())
                .build();
    }

    @Override
    @Transactional
    public AppliedBenefitSnapshot reserveAndApplyBenefits(
            User user,
            UsageTargetType targetType,
            UUID targetId,
            BenefitScope scope,
            BigDecimal itemsSubtotal,
            BigDecimal baseShippingFee
    ) {
        return reserveAndApplyBenefits(user, targetType, targetId, scope, itemsSubtotal, baseShippingFee, null, null);
    }

    @Override
    @Transactional
    public AppliedBenefitSnapshot reserveAndApplyBenefits(
            User user,
            UsageTargetType targetType,
            UUID targetId,
            BenefitScope scope,
            BigDecimal itemsSubtotal,
            BigDecimal baseShippingFee,
            BigDecimal customSubtotal,
            BigDecimal marketplaceSubtotal
    ) {
        BigDecimal safeSubtotal = nonNegative(itemsSubtotal);
        BigDecimal safeShip = nonNegative(baseShippingFee);
        if (user == null || targetType == null || targetId == null) {
            return AppliedBenefitSnapshot.empty(safeSubtotal, safeShip);
        }

        List<SubscriptionUsageLog> existingActive = usageLogRepository.findByTargetTypeAndTargetIdAndStatusIn(
                targetType.name(),
                targetId,
                List.of("RESERVED", "APPLIED")
        );
        if (!existingActive.isEmpty()) {
            return snapshotFromExistingLogs(existingActive, safeSubtotal, safeShip);
        }

        List<UserSubscription> lockedSubs = safeList(
                userSubscriptionRepository.findLockedActiveSubscriptionsByUser(user)
        );
        if (lockedSubs.isEmpty()) {
            return AppliedBenefitSnapshot.empty(safeSubtotal, safeShip);
        }

        EvaluationResult eval = evaluate(
                lockedSubs,
                scope != null ? scope : BenefitScope.MARKETPLACE,
                safeSubtotal,
                safeShip,
                customSubtotal,
                marketplaceSubtotal
        );

        List<SubscriptionUsageLog> logsToSave = new ArrayList<>();
        if (eval.discountSub() != null && eval.discountAmount().compareTo(BigDecimal.ZERO) > 0) {
            UserSubscription dSub = eval.discountSub();
            BigDecimal prevSaved = nonNegative(dSub.getDiscountAmountSavedTotal());
            dSub.setDiscountAmountSavedTotal(prevSaved.add(eval.discountAmount()));
            userSubscriptionRepository.save(dSub);

            logsToSave.add(buildUsageLog(dSub, user.getId(), targetType, targetId, "DISCOUNT", eval.discountAmount()));
        }

        if (eval.shippingSub() != null && eval.shippingDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
            UserSubscription sSub = eval.shippingSub();
            int used = sSub.getFreeShippingUsesUsed() != null ? sSub.getFreeShippingUsesUsed() : 0;
            sSub.setFreeShippingUsesUsed(used + 1);
            userSubscriptionRepository.save(sSub);

            logsToSave.add(buildUsageLog(
                    sSub, user.getId(), targetType, targetId, "FREE_SHIPPING", eval.shippingDiscountAmount()));
        }

        if (eval.prioritySub() != null && eval.priorityProduction()) {
            logsToSave.add(buildUsageLog(
                    eval.prioritySub(), user.getId(), targetType, targetId, "PRIORITY", BigDecimal.ZERO));
        }

        if (!logsToSave.isEmpty()) {
            usageLogRepository.saveAll(logsToSave);
        }

        UserSubscription appliedSub = resolveAppliedSubscription(eval);
        String appliedName = resolveCombinedAppliedName(eval, appliedSub);
        return new AppliedBenefitSnapshot(
                appliedSub != null ? appliedSub.getId() : null,
                appliedName,
                eval.itemsSubtotal(),
                eval.discountAmount(),
                eval.baseShippingFee(),
                eval.shippingDiscountAmount(),
                eval.finalShippingFee(),
                eval.finalTotal(),
                eval.priorityProduction()
        );
    }

    @Override
    @Transactional
    public void commitAppliedBenefits(UsageTargetType targetType, UUID targetId) {
        if (targetType == null || targetId == null) {
            return;
        }
        List<SubscriptionUsageLog> reserved = usageLogRepository.findByTargetTypeAndTargetIdAndStatus(
                targetType.name(), targetId, "RESERVED"
        );
        if (reserved.isEmpty()) {
            return;
        }
        for (SubscriptionUsageLog entry : reserved) {
            entry.setStatus("APPLIED");
        }
        usageLogRepository.saveAll(reserved);
    }

    @Override
    @Transactional
    public void releaseReservedBenefits(UsageTargetType targetType, UUID targetId) {
        if (targetType == null || targetId == null) {
            return;
        }
        List<SubscriptionUsageLog> reserved = usageLogRepository.findByTargetTypeAndTargetIdAndStatus(
                targetType.name(), targetId, "RESERVED"
        );
        if (reserved.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        for (SubscriptionUsageLog entry : reserved) {
            entry.setStatus("RELEASED");
            entry.setReleasedAt(now);

            UserSubscription sub = entry.getUserSubscription();
            if (sub == null) {
                continue;
            }
            if ("FREE_SHIPPING".equals(entry.getBenefitType())) {
                int used = sub.getFreeShippingUsesUsed() != null ? sub.getFreeShippingUsesUsed() : 0;
                sub.setFreeShippingUsesUsed(Math.max(0, used - 1));
                userSubscriptionRepository.save(sub);
            } else if ("DISCOUNT".equals(entry.getBenefitType())) {
                BigDecimal saved = nonNegative(sub.getDiscountAmountSavedTotal());
                BigDecimal delta = nonNegative(entry.getAmountSaved());
                sub.setDiscountAmountSavedTotal(saved.subtract(delta).max(BigDecimal.ZERO));
                userSubscriptionRepository.save(sub);
            }
        }
        usageLogRepository.saveAll(reserved);
    }

    @Override
    public Integer extractFreeShippingUsesLimit(SubscriptionPlan plan) {
        ParsedBenefits parsed = parseStructuredBenefits(plan != null ? plan.getStructuredBenefits() : null);
        if (parsed == null || parsed.shipping() == null) {
            return null;
        }
        return parsed.shipping().freeUsesPerTerm();
    }

    private EvaluationResult evaluate(
            List<UserSubscription> activeSubs,
            BenefitScope scope,
            BigDecimal itemsSubtotal,
            BigDecimal baseShippingFee,
            BigDecimal customSubtotal,
            BigDecimal marketplaceSubtotal
    ) {
        BigDecimal safeSubtotal = nonNegative(itemsSubtotal);
        BigDecimal safeShip = nonNegative(baseShippingFee);

        if (activeSubs == null || activeSubs.isEmpty()) {
            return new EvaluationResult(
                    false, null, null, 0, BigDecimal.ZERO,
                    null, BigDecimal.ZERO, null, 0, null,
                    null, false, safeSubtotal, safeShip, safeShip, safeSubtotal.add(safeShip), null
            );
        }

        UserSubscription primarySub = activeSubs.get(0);
        UserSubscription bestDiscountSub = null;
        int bestDiscountPercent = 0;
        BigDecimal bestDiscountAmount = BigDecimal.ZERO;

        UserSubscription bestShippingSub = null;
        BigDecimal bestShippingDiscount = BigDecimal.ZERO;
        Integer trackedLimit = null;
        int trackedUsed = 0;
        Integer trackedRemaining = null;
        boolean foundShippingQuotaInfo = false;

        UserSubscription prioritySub = null;
        boolean priorityProduction = false;
        String discountIneligibleReason = null;
        String shippingIneligibleReason = null;

        for (UserSubscription sub : activeSubs) {
            ParsedBenefits parsed = parseStructuredBenefits(
                    sub.getPlan() != null ? sub.getPlan().getStructuredBenefits() : null);
            if (parsed == null) {
                continue;
            }

            if (parsed.discount() != null) {
                BigDecimal eligibleSub = resolveEligibleDiscountSubtotal(
                        scope, safeSubtotal, customSubtotal, marketplaceSubtotal, parsed.discount().appliesTo());
                if (eligibleSub.compareTo(BigDecimal.ZERO) > 0 && parsed.discount().percent() > 0) {
                    BigDecimal minReq = parsed.discount().minEligibleSubtotal();
                    if (minReq != null && eligibleSub.compareTo(minReq) < 0) {
                        if (discountIneligibleReason == null) {
                            discountIneligibleReason = "Chưa đạt giá trị đơn tối thiểu "
                                    + minReq.setScale(0, RoundingMode.HALF_UP).toPlainString()
                                    + "đ để áp dụng giảm giá hội viên.";
                        }
                    } else {
                        BigDecimal rawDiscount = eligibleSub
                                .multiply(BigDecimal.valueOf(parsed.discount().percent()))
                                .divide(HUNDRED, 0, RoundingMode.HALF_UP);
                        BigDecimal maxCap = parsed.discount().maxDiscountAmount();
                        if (maxCap != null && maxCap.compareTo(BigDecimal.ZERO) > 0) {
                            rawDiscount = rawDiscount.min(maxCap);
                        }
                        rawDiscount = rawDiscount.min(safeSubtotal).max(BigDecimal.ZERO);

                        if (rawDiscount.compareTo(bestDiscountAmount) > 0) {
                            bestDiscountAmount = rawDiscount;
                            bestDiscountPercent = parsed.discount().percent();
                            bestDiscountSub = sub;
                        }
                    }
                } else if (parsed.discount().percent() > 0
                        && safeSubtotal.compareTo(BigDecimal.ZERO) > 0
                        && discountIneligibleReason == null) {
                    discountIneligibleReason = "Gói hội viên hiện tại không áp dụng giảm giá cho loại đơn này.";
                }
            }

            if (parsed.shipping() != null && appliesToScope(scope, parsed.shipping().appliesTo())) {
                Integer limit = parsed.shipping().freeUsesPerTerm();
                int used = sub.getFreeShippingUsesUsed() != null ? sub.getFreeShippingUsesUsed() : 0;
                Integer remaining = limit != null ? Math.max(0, limit - used) : null;

                if (!foundShippingQuotaInfo) {
                    trackedLimit = limit;
                    trackedUsed = used;
                    trackedRemaining = remaining;
                    foundShippingQuotaInfo = true;
                }

                boolean hasQuota = (limit == null) || (limit > 0 && used < limit);
                if (!hasQuota) {
                    if (shippingIneligibleReason == null) {
                        shippingIneligibleReason = "Đã hết lượt miễn phí vận chuyển trong kỳ.";
                    }
                } else if (safeShip.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal maxShipCap = parsed.shipping().maxDiscountAmountPerUse();
                    BigDecimal candidateShipDiscount;
                    if (maxShipCap == null) {
                        candidateShipDiscount = safeShip;
                    } else if (maxShipCap.compareTo(BigDecimal.ZERO) > 0) {
                        candidateShipDiscount = safeShip.min(maxShipCap);
                    } else {
                        candidateShipDiscount = BigDecimal.ZERO;
                    }
                    candidateShipDiscount = candidateShipDiscount.setScale(0, RoundingMode.HALF_UP)
                            .min(safeShip).max(BigDecimal.ZERO);

                    if (candidateShipDiscount.compareTo(bestShippingDiscount) > 0) {
                        bestShippingDiscount = candidateShipDiscount;
                        bestShippingSub = sub;
                        trackedLimit = limit;
                        trackedUsed = used;
                        trackedRemaining = remaining;
                        foundShippingQuotaInfo = true;
                    }
                }
            }

            if (parsed.production() != null && parsed.production().priorityEnabled()
                    && appliesToScope(scope, parsed.production().appliesTo())) {
                priorityProduction = true;
                if (prioritySub == null) {
                    prioritySub = sub;
                }
            }
        }

        BigDecimal netSubtotal = safeSubtotal.subtract(bestDiscountAmount).max(BigDecimal.ZERO);
        BigDecimal finalShipping = safeShip.subtract(bestShippingDiscount).max(BigDecimal.ZERO);
        BigDecimal finalTotal = netSubtotal.add(finalShipping);

        String reasonToReport = null;
        if (bestDiscountAmount.compareTo(BigDecimal.ZERO) == 0 && discountIneligibleReason != null) {
            reasonToReport = discountIneligibleReason;
        } else if (safeShip.compareTo(BigDecimal.ZERO) > 0
                && bestShippingDiscount.compareTo(BigDecimal.ZERO) == 0
                && shippingIneligibleReason != null) {
            reasonToReport = shippingIneligibleReason;
        }

        return new EvaluationResult(
                true,
                primarySub,
                bestDiscountSub,
                bestDiscountPercent,
                bestDiscountAmount,
                bestShippingSub,
                bestShippingDiscount,
                trackedLimit,
                trackedUsed,
                trackedRemaining,
                prioritySub,
                priorityProduction,
                safeSubtotal,
                safeShip,
                finalShipping,
                finalTotal,
                reasonToReport
        );
    }

    private BigDecimal resolveEligibleDiscountSubtotal(
            BenefitScope scope,
            BigDecimal itemsSubtotal,
            BigDecimal customSubtotal,
            BigDecimal marketplaceSubtotal,
            List<String> appliesTo
    ) {
        if (scope != BenefitScope.MIXED) {
            return appliesToScope(scope, appliesTo) ? itemsSubtotal : BigDecimal.ZERO;
        }
        boolean allowAll = appliesTo.contains("ALL");
        if (allowAll) {
            return itemsSubtotal;
        }
        boolean allowCustom = appliesTo.contains("CUSTOM");
        boolean allowMarket = appliesTo.contains("MARKETPLACE");
        if (allowCustom && allowMarket) {
            return itemsSubtotal;
        }
        if (!allowCustom && !allowMarket) {
            return BigDecimal.ZERO;
        }
        if (customSubtotal != null || marketplaceSubtotal != null) {
            BigDecimal sum = BigDecimal.ZERO;
            if (allowCustom && customSubtotal != null) {
                sum = sum.add(customSubtotal.max(BigDecimal.ZERO));
            }
            if (allowMarket && marketplaceSubtotal != null) {
                sum = sum.add(marketplaceSubtotal.max(BigDecimal.ZERO));
            }
            return sum.min(itemsSubtotal);
        }
        return itemsSubtotal;
    }

    private boolean appliesToScope(BenefitScope scope, List<String> appliesTo) {
        if (appliesTo == null || appliesTo.isEmpty() || appliesTo.contains("ALL")) {
            return true;
        }
        if (scope == BenefitScope.MIXED) {
            return appliesTo.contains("CUSTOM") || appliesTo.contains("MARKETPLACE");
        }
        return appliesTo.contains(scope.name());
    }

    private ParsedBenefits parseStructuredBenefits(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isObject()) {
                return null;
            }
            ParsedDiscount discount = null;
            JsonNode dNode = root.get("discount");
            if (dNode != null && dNode.isObject()) {
                discount = new ParsedDiscount(
                        dNode.path("percent").asInt(0),
                        parseAppliesTo(dNode.get("appliesTo")),
                        parseBigDecimal(dNode.get("minEligibleSubtotal")),
                        parseBigDecimal(dNode.get("maxDiscountAmount"))
                );
            }

            ParsedShipping shipping = null;
            JsonNode sNode = root.get("shipping");
            if (sNode != null && sNode.isObject()) {
                Integer freeUses = sNode.hasNonNull("freeUsesPerTerm")
                        ? sNode.get("freeUsesPerTerm").asInt()
                        : null;
                shipping = new ParsedShipping(
                        freeUses,
                        parseBigDecimal(sNode.get("maxDiscountAmountPerUse")),
                        parseAppliesTo(sNode.get("appliesTo"))
                );
            }

            ParsedProduction production = null;
            JsonNode pNode = root.get("production");
            if (pNode != null && pNode.isObject()) {
                production = new ParsedProduction(
                        pNode.path("priorityEnabled").asBoolean(false),
                        parseAppliesTo(pNode.get("appliesTo"))
                );
            }

            return new ParsedBenefits(discount, shipping, production);
        } catch (Exception ex) {
            log.warn("Failed to parse structuredBenefits JSON: {}", ex.getMessage());
            return null;
        }
    }

    private List<String> parseAppliesTo(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of("ALL");
        }
        List<String> values = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode el : node) {
                if (el != null && !el.isNull()) {
                    String normalized = el.asText("").trim().toUpperCase(Locale.ROOT);
                    if (!normalized.isEmpty()) {
                        values.add(normalized);
                    }
                }
            }
        } else if (node.isTextual()) {
            String normalized = node.asText("").trim().toUpperCase(Locale.ROOT);
            if (!normalized.isEmpty()) {
                values.add(normalized);
            }
        }
        return values.isEmpty() ? List.of("ALL") : values;
    }

    private BigDecimal parseBigDecimal(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isTextual()) {
            String raw = node.asText("").trim();
            if (!raw.isEmpty()) {
                try {
                    return new BigDecimal(raw);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private AppliedBenefitSnapshot snapshotFromExistingLogs(
            List<SubscriptionUsageLog> logs,
            BigDecimal itemsSubtotal,
            BigDecimal baseShippingFee
    ) {
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal shippingDiscount = BigDecimal.ZERO;
        boolean priority = false;
        UserSubscription primarySub = null;
        Set<String> planNames = new LinkedHashSet<>();

        for (SubscriptionUsageLog entry : logs) {
            if (primarySub == null) {
                primarySub = entry.getUserSubscription();
            }
            if (entry.getUserSubscription() != null && entry.getUserSubscription().getPlan() != null) {
                planNames.add(entry.getUserSubscription().getPlan().getName());
            }
            if ("DISCOUNT".equals(entry.getBenefitType())) {
                discount = discount.add(nonNegative(entry.getAmountSaved()));
                primarySub = entry.getUserSubscription();
            } else if ("FREE_SHIPPING".equals(entry.getBenefitType())) {
                shippingDiscount = shippingDiscount.add(nonNegative(entry.getAmountSaved()));
                if (primarySub == null) {
                    primarySub = entry.getUserSubscription();
                }
            } else if ("PRIORITY".equals(entry.getBenefitType())) {
                priority = true;
                if (primarySub == null) {
                    primarySub = entry.getUserSubscription();
                }
            }
        }

        discount = discount.min(itemsSubtotal);
        shippingDiscount = shippingDiscount.min(baseShippingFee);
        BigDecimal finalShipping = baseShippingFee.subtract(shippingDiscount).max(BigDecimal.ZERO);
        BigDecimal finalTotal = itemsSubtotal.subtract(discount).max(BigDecimal.ZERO).add(finalShipping);
        String appliedName = !planNames.isEmpty()
                ? String.join(" + ", planNames)
                : (primarySub != null && primarySub.getPlan() != null ? primarySub.getPlan().getName() : null);
        if (appliedName != null && appliedName.length() > 200) {
            appliedName = appliedName.substring(0, 200);
        }

        return new AppliedBenefitSnapshot(
                primarySub != null ? primarySub.getId() : null,
                appliedName,
                itemsSubtotal,
                discount,
                baseShippingFee,
                shippingDiscount,
                finalShipping,
                finalTotal,
                priority
        );
    }

    private SubscriptionUsageLog buildUsageLog(
            UserSubscription sub,
            UUID userId,
            UsageTargetType targetType,
            UUID targetId,
            String benefitType,
            BigDecimal amountSaved
    ) {
        return SubscriptionUsageLog.builder()
                .userSubscription(sub)
                .userId(userId)
                .targetType(targetType.name())
                .targetId(targetId)
                .benefitType(benefitType)
                .status("RESERVED")
                .amountSaved(nonNegative(amountSaved))
                .createdAt(Instant.now())
                .build();
    }

    private UserSubscription resolveMainSubscription(EvaluationResult eval) {
        UserSubscription applied = resolveAppliedSubscription(eval);
        return applied != null ? applied : eval.primarySub();
    }

    private UserSubscription resolveAppliedSubscription(EvaluationResult eval) {
        if (eval.discountSub() != null) {
            return eval.discountSub();
        }
        if (eval.shippingSub() != null) {
            return eval.shippingSub();
        }
        return eval.prioritySub();
    }

    private String resolveCombinedAppliedName(EvaluationResult eval, UserSubscription fallbackSub) {
        Set<String> names = new LinkedHashSet<>();
        if (eval.discountSub() != null && eval.discountSub().getPlan() != null) {
            names.add(eval.discountSub().getPlan().getName());
        }
        if (eval.shippingSub() != null && eval.shippingSub().getPlan() != null) {
            names.add(eval.shippingSub().getPlan().getName());
        }
        if (eval.prioritySub() != null && eval.prioritySub().getPlan() != null && names.isEmpty()) {
            names.add(eval.prioritySub().getPlan().getName());
        }
        if (names.isEmpty()) {
            return fallbackSub != null && fallbackSub.getPlan() != null ? fallbackSub.getPlan().getName() : null;
        }
        String joined = String.join(" + ", names);
        return joined.length() > 200 ? joined.substring(0, 200) : joined;
    }

    private BigDecimal nonNegative(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0 ? value : BigDecimal.ZERO;
    }

    private List<UserSubscription> safeList(List<UserSubscription> list) {
        return list != null ? list : List.of();
    }
}
