package com.fpt.printhub_3d.common.init;

import com.fpt.printhub_3d.entity.*;
import com.fpt.printhub_3d.entity.Enumeration.*;
import com.fpt.printhub_3d.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final CustomPrintServiceRepository customPrintServiceRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        log.info("🚀 Kiểm tra & Khởi tạo dữ liệu mẫu cho CSDL PrintHub 3D...");

        // 1. Khởi tạo Categories nếu chưa có
        try {
            if (categoryRepository.count() == 0) {
                initCategories();
            }
        } catch (Exception e) {
            log.warn("⚠️ Không thể khởi tạo Categories: {}", e.getMessage());
        }

        // 2. Khởi tạo Users nếu chưa có
        User seller = null;
        try {
            if (userRepository.count() == 0) {
                seller = initUsers();
            } else {
                seller = userRepository.findByEmailOrUsername("admin@printhub3d.com", "admin")
                        .orElse(userRepository.findAll().stream().findFirst().orElse(null));
            }
        } catch (Exception e) {
            log.warn("⚠️ Không thể khởi tạo Users: {}", e.getMessage());
        }

        // 3. Khởi tạo Subscription Plans nếu chưa có
        try {
            if (subscriptionPlanRepository.count() == 0) {
                initSubscriptionPlans();
            }
        } catch (Exception e) {
            log.warn("⚠️ Bỏ qua SubscriptionPlans do bảng DB SQL Server cũ dùng BIGINT thay vì UUID: {}", e.getMessage());
        }

        // 4. Khởi tạo 5 Sản phẩm Thước kẻ in 3D & các sản phẩm khác nếu chưa có
        try {
            if (productRepository.count() == 0 && seller != null) {
                initProducts(seller);
            }
        } catch (Exception e) {
            log.warn("⚠️ Không thể khởi tạo 5 sản phẩm Thước kẻ: {}", e.getMessage());
        }

        // 5. Khởi tạo Custom Print Services nếu chưa có
        try {
            if (customPrintServiceRepository.count() == 0 && seller != null) {
                initCustomPrintServices(seller);
            }
        } catch (Exception e) {
            log.warn("⚠️ Không thể khởi tạo CustomPrintServices: {}", e.getMessage());
        }

        log.info("✅ Đã hoàn tất khởi tạo dữ liệu mẫu CSDL!");
    }

    private void initCategories() {
        log.info("📦 Tạo danh mục sản phẩm mẫu...");
        categoryRepository.saveAll(List.of(
                Category.builder().categoryName("Thước kẻ & Dụng cụ học tập 3D").description("Các loại thước kẻ, eke, đo góc in 3D cá nhân hóa").build(),
                Category.builder().categoryName("Phụ kiện máy in 3D & Cuộn nhựa").description("Cuộn nhựa PLA, PETG, Resin và linh kiện máy in 3D").build(),
                Category.builder().categoryName("Mô hình 3D thành phẩm").description("Mô hình trang trí, replica máy in 3D và nhân vật 3D").build(),
                Category.builder().categoryName("Dịch vụ in theo yêu cầu").description("Nhận thiết kế & in 3D mẫu custom theo file STL").build()
        ));
    }

    private User initUsers() {
        log.info("👤 Tạo tài khoản mẫu...");
        String encodedPassword = passwordEncoder.encode("123456@Abc");

        User admin = User.builder()
                .username("admin")
                .email("admin@printhub3d.com")
                .password(encodedPassword)
                .fullName("Quản Trị Viên System")
                .phone("0900000001")
                .address("Tòa nhà FPT, Quận 9, TP.HCM")
                .role(UserRole.ADMIN)
                .isActive(true)
                .rewardPoints(1000)
                .build();
        userRepository.save(admin);

        User buyer = User.builder()
                .username("buyer1")
                .email("buyer1@printhub3d.com")
                .password(encodedPassword)
                .fullName("Nguyễn Văn Anh")
                .phone("0987654321")
                .address("456 Đường Nguyễn Trãi, Thanh Xuân, Hà Nội")
                .role(UserRole.USER)
                .isActive(true)
                .rewardPoints(100)
                .build();
        userRepository.save(buyer);

        return admin;
    }

    private void initSubscriptionPlans() {
        log.info("💎 Tạo gói hội viên mẫu...");
        subscriptionPlanRepository.saveAll(List.of(
                SubscriptionPlan.builder()
                        .name("Gói Khách Hàng VIP Gold")
                        .type(SubscriptionType.CUSTOMER)
                        .price(new BigDecimal("99000"))
                        .benefits("Giảm 15% cho mọi đơn thước kẻ & mô hình, miễn phí giao hàng toàn quốc.")
                        .requiredPoints(100)
                        .isActive(true)
                        .build(),
                SubscriptionPlan.builder()
                        .name("Gói Khách Hàng Premium")
                        .type(SubscriptionType.CUSTOMER)
                        .price(new BigDecimal("199000"))
                        .benefits("Giảm 20% cho mọi đơn hàng, ưu tiên in hàng gấp trong 24h.")
                        .requiredPoints(250)
                        .isActive(true)
                        .build()
        ));
    }

    private void initProducts(User seller) {
        log.info("📏 Tạo 5 sản phẩm Thước kẻ in 3D mẫu...");
        Category rulerCat = categoryRepository.findAll().stream()
                .filter(c -> c.getCategoryName().contains("Thước kẻ"))
                .findFirst()
                .orElse(categoryRepository.findAll().get(0));

        Instant now = Instant.now();

        // 1. Thước kẻ Bambu Lab X1-Carbon Replica
        Product p1 = new Product();
        p1.setSeller(seller);
        p1.setCategory(rulerCat);
        p1.setTitle("Bambu Lab X1-Carbon Mini Replica (Bộ Thước Kẻ 3D)");
        p1.setDescription("Bộ thước kẻ 3D cá nhân hóa cao cấp khắc tên theo công thức Toán 12. Mô phỏng độc quyền kiểu dáng máy in Bambu Lab X1-Carbon.");
        p1.setPrice(new BigDecimal("450000"));
        p1.setStock(50);
        p1.setType("PHYSICAL");
        p1.setStatus("ACTIVE");
        p1.setCreatedAt(now);
        p1.setUpdatedAt(now);
        productRepository.save(p1);
        savePrimaryImage(p1, "https://images.unsplash.com/photo-1581092160607-ee22621dd758?auto=format&fit=crop&w=600&q=80");

        // 2. Thước Eke In 3D Tough PLA Neon
        Product p2 = new Product();
        p2.setSeller(seller);
        p2.setCategory(rulerCat);
        p2.setTitle("Thước Kẻ Tam Giác Eke In 3D Nhựa Tough PLA Neon");
        p2.setDescription("Thước eke in 3D chính xác millimeter, sử dụng nhựa Tough PLA chịu lực cực tốt, tùy chọn màu Neon phản quang rực rỡ.");
        p2.setPrice(new BigDecimal("150000"));
        p2.setStock(100);
        p2.setType("PHYSICAL");
        p2.setStatus("ACTIVE");
        p2.setCreatedAt(now);
        p2.setUpdatedAt(now);
        productRepository.save(p2);
        savePrimaryImage(p2, "https://images.unsplash.com/photo-1585336261026-8f5786372966?auto=format&fit=crop&w=600&q=80");

        // 3. Thước Đo Góc 180 Độ In 3D Khắc Tên
        Product p3 = new Product();
        p3.setSeller(seller);
        p3.setCategory(rulerCat);
        p3.setTitle("Thước Đo Góc 180 Độ In 3D Khắc Tên Theo Yêu Cầu");
        p3.setDescription("Thước bán nguyệt đo góc 180 độ in 3D có tích hợp ô khắc tên riêng, niên khóa và logo lớp học siêu độc đáo.");
        p3.setPrice(new BigDecimal("180000"));
        p3.setStock(75);
        p3.setType("PHYSICAL");
        p3.setStatus("ACTIVE");
        p3.setCreatedAt(now);
        p3.setUpdatedAt(now);
        productRepository.save(p3);
        savePrimaryImage(p3, "https://images.unsplash.com/photo-1509228468518-180dd4864904?auto=format&fit=crop&w=600&q=80");

        // 4. Bộ Thước Đa Năng 4 Trong 1 In 3D PETG Super Clear
        Product p4 = new Product();
        p4.setSeller(seller);
        p4.setCategory(rulerCat);
        p4.setTitle("Bộ Thước Đa Năng 4 Trong 1 In 3D Nhựa PETG Trong Suốt");
        p4.setDescription("Combo 4 thước gồm thước thẳng 20cm, eke 45 độ, đo góc và thước cong vẽ kỹ thuật in bằng nhựa PETG trong suốt chống va đập.");
        p4.setPrice(new BigDecimal("250000"));
        p4.setStock(30);
        p4.setType("PHYSICAL");
        p4.setStatus("ACTIVE");
        p4.setCreatedAt(now);
        p4.setUpdatedAt(now);
        productRepository.save(p4);
        savePrimaryImage(p4, "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=600&q=80");

        // 5. Thước Cuộn Mini In 3D Vỏ Carbon
        Product p5 = new Product();
        p5.setSeller(seller);
        p5.setCategory(rulerCat);
        p5.setTitle("Thước Cuộn Mini Bỏ Túi 2m In 3D Vỏ Carbon Khắc Tên");
        p5.setDescription("Thước cuộn mini 2m in 3D với vỏ khung Carbon siêu bền và cơ chế tự rút thông minh, thiết kế chuyên dụng cho kiến trúc sư và Maker 3D.");
        p5.setPrice(new BigDecimal("320000"));
        p5.setStock(40);
        p5.setType("PHYSICAL");
        p5.setStatus("ACTIVE");
        p5.setCreatedAt(now);
        p5.setUpdatedAt(now);
        productRepository.save(p5);
        savePrimaryImage(p5, "https://images.unsplash.com/photo-1572981779307-38b8cabb2407?auto=format&fit=crop&w=600&q=80");
    }

    private void savePrimaryImage(Product product, String imageUrl) {
        ProductImage image = new ProductImage();
        image.setProduct(product);
        image.setImageUrl(imageUrl);
        image.setIsPrimary(true);
        productImageRepository.save(image);
    }

    private void initCustomPrintServices(User seller) {
        log.info("🛠️ Tạo Dịch vụ In Custom mẫu...");
        CustomPrintService service = CustomPrintService.builder()
                .maker(seller)
                .serviceName("Dịch Vụ In 3D Kỹ Thuật FDM & Resin Độ Nét Cao")
                .description("Nhận in 3D theo file STL/OBJ của khách hàng. Hỗ trợ đầy đủ vật liệu nhựa PLA, ABS, PETG, Resin Tough và thiết kế cá nhân hóa.")
                .printerModels(List.of("Bambu Lab X1-Carbon", "Ender 3 V2 Pro", "Anycubic Photon Mono X"))
                .supportedMaterials(List.of("PLA Neon", "ABS Tough", "Resin Standard", "PETG Clear"))
                .minimumPrice(new BigDecimal("50000"))
                .maxPrintSize("300x300x400 mm")
                .estimatedProductionDays(2)
                .build();
        customPrintServiceRepository.save(service);
    }
}
