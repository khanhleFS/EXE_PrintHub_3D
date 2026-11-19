# Hợp đồng BE1 cho FE và BE2

Bản đề xuất cho việc 1, đối chiếu với nhánh `feat/fix` tại commit `7cdfdc6`. Các JSON dưới đây là dữ liệu mẫu bàn giao. Tên trường mới và hành vi minh họa sẽ được triển khai ở các việc tiếp theo.

## Dữ liệu chung

U1 mua gói G1 với giá 50.000đ trong giao dịch S1. G1 giảm 10% tiền hàng custom và có một lượt giảm phí giao tối đa 30.000đ. C1 là checkout của U1, có một thiết kế in một bản, đơn giá 100.000đ và phí giao 30.000đ. Tiền hàng sau giảm là 90.000đ, phí giao sau giảm là 0đ và tổng phải trả là 90.000đ.

## Hợp đồng cho FE

Giữ envelope `ApiResponse`, đường dẫn và trường hiện có. Bổ sung `pricingBreakdown` và `paymentEvidence` trên response checkout và chi tiết custom lẻ. Các mẫu chỉ liệt kê trường cần cho luồng này. FE dùng `pricingBreakdown.totalPayable` để hiển thị số phải trả.

| Trường | Quy ước |
|---|---|
| `pricingStage` | `PENDING_QUOTE` khi còn dòng chưa báo giá, `ESTIMATED` khi đủ báo giá, `LOCKED` sau khi xác nhận. Khi còn chờ báo giá, `totalPayable` là `null`. |
| `quoteVersion` | Trong bảng tiền checkout, lấy từ `summaryVersion`. Đơn custom lẻ dùng `quoteVersion` riêng. |
| Các trường tiền | Số JSON theo đơn vị VND. Giảm giá là số dương. `totalPayable = itemsSubtotal - itemDiscountAmount + finalShippingFee`. Ship bằng 0 là giá trị hợp lệ. |
| `benefitStatus` | `APPLIED`, `NO_SUBSCRIPTION`, `EXPIRED`, `INELIGIBLE`, `QUOTA_EXHAUSTED`, `CONFIG_REQUIRED`. `APPLIED` ở bản dự kiến nghĩa là đã tính vào bảng tiền xem trước. Trả thêm `ineligibleReason` khi có lý do, kể cả khi vẫn áp dụng được một quyền lợi khác. |
| `paymentEvidence.status` | `NONE` khi chưa có chứng từ, `PENDING` khi đang chờ thu, `SETTLED` khi đã thu hợp lệ, `PENDING_RECONCILIATION` khi cần đối soát, `ZERO_AMOUNT_PROMO` khi đã xác nhận đơn ưu đãi toàn bộ. Đây là trạng thái chứng từ, tách khỏi tiến trình đơn và trường `paymentStatus` hiện có. |

### C1 đã có báo giá

`GET /api/checkout-orders/{id}`, HTTP 200. API danh sách `GET /api/checkout-orders/my` dùng cùng bảng tiền trên từng đơn. Xem dự kiến không tiêu lượt ưu đãi.

```json
{
  "code": 200,
  "result": {
    "id": "10000000-0000-4000-8000-000000000001",
    "status": "QUOTED",
    "summaryVersion": 2,
    "customOrders": [
      {
        "id": "20000000-0000-4000-8000-000000000001",
        "unitQuotedPrice": 100000,
        "quotedPrice": 100000
      }
    ],
    "pricingBreakdown": {
      "pricingStage": "ESTIMATED",
      "quoteVersion": 2,
      "customItemsSubtotal": 100000,
      "marketplaceItemsSubtotal": 0,
      "itemsSubtotal": 100000,
      "itemDiscountAmount": 10000,
      "baseShippingFee": 30000,
      "shippingDiscountAmount": 30000,
      "finalShippingFee": 0,
      "totalPayable": 90000,
      "zeroAmountSettlement": false,
      "appliedSubscriptionId": "30000000-0000-4000-8000-000000000001",
      "appliedSubscriptionName": "Gói G1",
      "benefitStatus": "APPLIED",
      "ineligibleReason": null,
      "priorityProduction": false
    },
    "paymentEvidence": { "status": "NONE" }
  }
}
```

### C1 xác nhận giá và chờ PayOS

`POST /api/checkout-orders/{id}/confirm`. FE gửi `expectedTotalPayable` kèm phiên bản cho lần xác nhận giá đầu tiên. Backend tính lại từ dữ liệu của mình. Nếu thiếu số tiền hoặc số tiền khác tổng vừa tính, backend trả HTTP 409 với bảng tiền mới. Các trường đọc cũ vẫn có trên DTO.

```json
{
  "summaryVersion": 2,
  "paymentMethod": "PAYOS",
  "expectedTotalPayable": 90000
}
```

HTTP 200. Bảng tiền giữ cùng cấu trúc và số tiền như bản dự kiến, chuyển sang `LOCKED` và được lưu tại thời điểm xác nhận. Link dùng số đã chốt. Xác nhận lặp cùng phiên bản và số tiền đã chốt dùng lại kết quả khi giao dịch vẫn còn hiệu lực.

```json
{
  "code": 200,
  "result": {
    "id": "10000000-0000-4000-8000-000000000001",
    "status": "CONFIRMED",
    "summaryVersion": 2,
    "paymentMethod": "PAYOS",
    "paymentStatus": "PENDING",
    "paymentLinkUrl": "https://example.invalid/payos/c1",
    "pricingBreakdown": {
      "pricingStage": "LOCKED",
      "quoteVersion": 2,
      "customItemsSubtotal": 100000,
      "marketplaceItemsSubtotal": 0,
      "itemsSubtotal": 100000,
      "itemDiscountAmount": 10000,
      "baseShippingFee": 30000,
      "shippingDiscountAmount": 30000,
      "finalShippingFee": 0,
      "totalPayable": 90000,
      "zeroAmountSettlement": false,
      "appliedSubscriptionId": "30000000-0000-4000-8000-000000000001",
      "appliedSubscriptionName": "Gói G1",
      "benefitStatus": "APPLIED",
      "ineligibleReason": null,
      "priorityProduction": false
    },
    "paymentEvidence": { "status": "PENDING" }
  }
}
```

Đơn custom lẻ dùng `PUT /api/custom-orders/{id}/status` với `status: ACCEPTED`, `paymentMethod: PAYOS`, `quoteVersion` và `expectedTotalPayable`. API đọc chi tiết `GET /api/custom-orders/{id}` trả bảng tiền đã chốt. Đây là luồng đơn lẻ. Với dòng thuộc checkout, FE xác nhận trên đơn cha.

Khi checkout đã xác nhận nhưng `paymentLinkUrl` còn trống, FE có thể gọi lại `POST /api/payments/create-link` cho cùng đơn. Đơn custom lẻ gọi endpoint này sau khi chấp nhận. Backend dùng lại mã giao dịch và tổng đã chốt.

### C1 đã thu tiền

`GET /api/checkout-orders/{id}`, HTTP 200. Ví dụ rút gọn phần đã thay đổi sau khi webhook hoặc `GET /api/payments/verify/{orderCode}` xác nhận thanh toán. Toàn bộ bảng tiền `LOCKED` vẫn được trả khi đọc đơn và giữ số phải trả 90.000đ.

```json
{
  "code": 200,
  "result": {
    "id": "10000000-0000-4000-8000-000000000001",
    "status": "PAID",
    "summaryVersion": 2,
    "paymentStatus": "SUCCESS",
    "paymentEvidence": {
      "status": "SETTLED",
      "paymentId": "40000000-0000-4000-8000-000000000001",
      "gateway": "PAYOS",
      "paidAmount": 90000,
      "paidAt": "2026-11-19T03:15:00Z"
    }
  }
}
```

### Tổng thay đổi khi khách xác nhận

HTTP 409 trong ví dụ hai đơn tranh lượt freeship cuối. Cùng phiên bản 2 nhưng giá tính lại là 120.000đ. FE hiển thị bảng mới và chờ khách xác nhận lại với phiên bản và số tiền mới. Yêu cầu bị từ chối không giữ lượt mới hay tạo link.

```json
{
  "code": 409,
  "errorKey": "checkout.price_changed",
  "message": "Số tiền phải trả đã thay đổi. Vui lòng xem lại và xác nhận.",
  "result": {
    "id": "10000000-0000-4000-8000-000000000001",
    "summaryVersion": 2,
    "pricingBreakdown": {
      "pricingStage": "ESTIMATED",
      "quoteVersion": 2,
      "customItemsSubtotal": 100000,
      "marketplaceItemsSubtotal": 0,
      "itemsSubtotal": 100000,
      "itemDiscountAmount": 10000,
      "baseShippingFee": 30000,
      "shippingDiscountAmount": 0,
      "finalShippingFee": 30000,
      "totalPayable": 120000,
      "zeroAmountSettlement": false,
      "appliedSubscriptionId": "30000000-0000-4000-8000-000000000001",
      "appliedSubscriptionName": "Gói G1",
      "benefitStatus": "QUOTA_EXHAUSTED",
      "ineligibleReason": "Gói hội viên đã hết lượt miễn phí giao hàng.",
      "priorityProduction": false
    }
  }
}
```

Khi xưởng đổi báo giá, backend dùng `checkout.version_conflict` và trả phiên bản mới trong cùng cấu trúc `result`. Với custom lẻ, mã tương ứng là `custom_print.price_changed` hoặc `custom_print.version_conflict`, và `result` dùng `quoteVersion` ở cấp đơn. Nếu API đọc bảng tiền lỗi, FE giữ trạng thái lỗi và cho thử lại.

### Đơn được ưu đãi toàn bộ

FE gửi `expectedTotalPayable: 0` khi xác nhận bảng tiền dự kiến 0đ. Backend kiểm tra lại phiên bản và quota như các đơn khác. Khi xác nhận thành công, đơn vào trạng thái `PAID`, bảng tiền `LOCKED` có `totalPayable: 0` và `zeroAmountSettlement: true`. FE hiển thị “Được ưu đãi toàn bộ” và bỏ qua bước mở PayOS. Trường `paymentEvidence` dùng cấu trúc dưới đây. `paidAt` là mốc xác nhận miễn phí đã lưu. Đơn custom lẻ đọc lại chi tiết sau khi gọi API chấp nhận báo giá.

Mốc này nằm ở cột nullable `zero_amount_settled_at` của `checkout_orders` hoặc `custom_orders` cho đơn custom lẻ. Các dòng thuộc checkout dùng mốc của đơn cha.

```json
{
  "status": "ZERO_AMOUNT_PROMO",
  "gateway": "ZERO_AMOUNT_PROMO",
  "paidAmount": 0,
  "paidAt": "2026-11-19T03:15:00Z"
}
```

## Hợp đồng cho BE2

### Chứng từ và mốc hoàn tất

Dùng chữ ký Java hiện có trong `PaymentEvidenceService`.

```java
Optional<PaymentEvidence> findSettledEvidence(SourceType sourceType, UUID sourceId);
PaymentEvidence recordCodCollection(SourceType sourceType, UUID sourceId,
                                    BigDecimal collectedAmount, User admin);
```

- Đơn con thuộc checkout tra chứng từ của cha `CHECKOUT_ORDER`. Đơn độc lập tra theo `ORDER` hoặc `CUSTOM_ORDER`. Chứng từ hợp lệ trả ID, nguồn, cổng, số tiền và `paidAt`. Khoản chờ thu hoặc đang đối soát trả `Optional.empty()`.
- Đơn ưu đãi toàn bộ trả căn cứ có `zeroAmountPromo = true`, `paidAmount = 0`, `paymentId = null`, `gateway = ZERO_AMOUNT_PROMO` và `paidAt` lấy từ mốc xác nhận miễn phí đã lưu. BE2 tính tiền hàng đủ điều kiện từ bảng giá đã chốt, loại phí giao. Interface hiện tại không có trường tiền hàng sau giảm. C1 có cơ sở tính xu 90.000đ và nhận 9 xu sau giao. Đơn ưu đãi toàn bộ được xét 0 xu.
- BE2 gọi `recordCodCollection` khi admin xác nhận đã thu trên đơn hỗ trợ COD. BE1 ghi một chứng từ theo nguồn thu và trả cùng `paymentId` khi gọi lặp. Giá trị COD bàn giao là số thực thu do thao tác xác nhận cung cấp.
- Theo phân công đề xuất, BE2 ghi `completedAt` ở lần đầu chuyển sang `COMPLETED`, ưu tiên checkout cha khi tính theo đơn cha. BE1 đọc mốc đó cho báo cáo. `paidAt` là mốc thu tiền. Dữ liệu cũ thiếu ngày giao đáng tin cậy được đưa vào đối soát.

### Sự kiện email

Dùng các record và phương thức hiện có trong `EmailOutboxService`.

| Điểm gọi của BE1 | Dữ liệu và khóa chống lặp |
|---|---|
| `enqueueQuotationIssued(QuotationEmailEvent)` | Gọi trong transaction phát hành báo giá checkout đủ dòng hoặc đơn custom lẻ. Gửi bảng tiền dự kiến cùng các dòng báo giá. Khóa gồm `sourceType`, `sourceId`, `quoteVersion`. |
| `enqueuePaymentReceipt(PaymentReceiptEmailEvent)` | Gọi trong transaction xác nhận thu PayOS/COD hợp lệ lần đầu. Dùng `paymentId` đã lưu làm khóa. C1 dùng nguồn `CHECKOUT_ORDER`, S1 dùng `SUBSCRIPTION_PURCHASE`. Chỉ ghi sự kiện với khoản thu tiền hợp lệ lớn hơn 0. |

BE2 phụ trách worker gửi và thử lại. Khi nghiệm thu C1 và S1, kỳ vọng một email báo giá C1 và hai biên nhận tương ứng 90.000đ và 50.000đ.
