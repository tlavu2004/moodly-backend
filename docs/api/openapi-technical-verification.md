# Việc cần làm: kiểm tra kỹ thuật hợp đồng OpenAPI

## Hiện tại CI bảo đảm điều gì?

`src/main/resources/scripts/openapi/generate-openapi.sh` chạy ứng dụng với profile `docs`, lấy `/v3/api-docs` và ghi vào `docs/api/moodly-openapi.json`. Trong `.github/workflows/ci.yml`, CI kiểm tra file đọc được dưới dạng JSON, có phiên bản OpenAPI 3 và có `paths`; sau đó dùng `git diff --exit-code` để so bản vừa sinh với bản đã commit. Bước này phát hiện **tài liệu chưa được cập nhật theo code**, không xác nhận mọi schema đều mô tả đúng response thực tế.

`ApiIntegrationTest` đã kiểm tra một số chi tiết của `/v3/api-docs` và các HTTP response, nhưng chưa đối chiếu chúng một cách tổng quát.

## Bổ sung ở lượt refactor sau

1. **Kiểm tra tài liệu OpenAPI:** dùng công cụ hiểu OpenAPI 3.1 để kiểm tra cấu trúc và giải được tất cả `$ref` nội bộ. Khi sai, CI cần chỉ ra vị trí tham chiếu và đích bị thiếu. Việc JSON parse được và có `paths` chưa đủ.
2. **Đối chiếu với HTTP thực tế:** với các endpoint và trường hợp đại diện, gọi API qua integration test, rồi kiểm tra status, content type và JSON body theo response schema OpenAPI tương ứng. Bao gồm response thành công, lỗi validation/xác thực và các trường nullable hoặc envelope dùng chung. Ghi rõ những endpoint/trường hợp được bao phủ; một vài mẫu response không chứng minh mọi giá trị runtime đều đúng.
3. **Kiểm tra tính ổn định của quá trình sinh:** sinh tài liệu hai lần trong cùng môi trường CI từ cùng revision và so sánh kết quả sau chuẩn hóa. Nếu khác nhau, sửa nguồn gây thứ tự hoặc giá trị không ổn định trong generator; không cập nhật snapshot để che sự khác biệt. Tiếp tục giữ bước so bản sinh ra với file đã commit để kiểm tra tính cập nhật.

## Điều kiện hoàn thành

- Ba kiểm tra trên chạy trong CI và có thông báo lỗi đủ rõ để tìm đến schema, endpoint hoặc phần sinh tài liệu gây sai lệch.
- Chạy lại nhiều lần từ cùng revision cho kết quả giống nhau; test đối chiếu HTTP chứng minh các trường hợp đã chọn khớp schema.
- Giữ quy trình hiện tại: khi code ảnh hưởng OpenAPI, chạy script sinh tài liệu, review diff và commit `docs/api/moodly-openapi.json` cùng code.

Đây là việc **kiểm tra tính đúng đắn kỹ thuật**, chưa đánh giá quy tắc nghiệp vụ hay quyết định API có phù hợp sản phẩm hay không.
