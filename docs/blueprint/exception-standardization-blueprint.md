# Chuẩn hóa Exception cho Moodly (tham chiếu NovaCart)

> **Mục đích:** NovaCart là mẫu tham chiếu. Phần 7 bên dưới là quy ước và kiểm kê thực tế cho Moodly; giữ cấu trúc file và API contract hiện có khi triển khai.

## 1. Nền tảng tham chiếu trong NovaCart

NovaCart tách trách nhiệm theo tầng:

| Thành phần | Vai trò | Nguồn tham chiếu |
|---|---|---|
| `ErrorCode` | Giao diện chung gồm `getCode()` và `getDefaultMessage()` | `shared/application/exception/code/contract/ErrorCode.java` |
| `GlobalErrorCode` | Mã dùng chung cho validation, HTTP và lỗi hệ thống (`VAL_*`, `SYS_*`) | `shared/application/exception/code/global/GlobalErrorCode.java` |
| `CatalogErrorCode` | Mã nghiệp vụ theo module (`CAT_*`, `PROD_*`) | `modules/catalog/shared/application/exception/code/catalog/CatalogErrorCode.java` |
| `BaseException` | Lớp trừu tượng kế thừa `RuntimeException`, mang `ErrorCode` và `HttpStatus` | `shared/application/exception/base/BaseException.java` |
| Exception chung | Cố định nhóm HTTP: `InvalidInputException` → 400, `ResourceNotFoundException` → 404, `ConflictException` → 409 | `shared/application/exception/common/` |
| Exception nghiệp vụ | Tên theo tình huống; kế thừa nhóm phù hợp và gắn mã cụ thể, ví dụ `ProductNotFoundException` | `modules/catalog/*/application/exception/` |
| `GlobalExceptionHandler` | `@RestControllerAdvice` ánh xạ exception thành response thống nhất | `shared/presentation/advice/GlobalExceptionHandler.java` |

Luồng điển hình: nghiệp vụ ném `ProductNotFoundException` → lớp cha `ResourceNotFoundException` đặt HTTP 404 → `@ExceptionHandler(BaseException.class)` đọc mã và thông điệp → trả `ApiResponse.error(ApiError)` với `status`, `code`, `message`, `path`, `fieldErrors` và `timestamp`. Handler riêng xử lý lỗi validation, JSON, tham số, phương thức HTTP; handler `Exception` cuối cùng trả mã lỗi nội bộ và thông điệp chung.

**Ranh giới tầng:** Domain có thể ném exception Java thuần để diễn đạt quy tắc nghiệp vụ, không phụ thuộc Spring HTTP hay cấu trúc API. Ví dụ `product/domain/exception/InvalidProductStatusTransitionException` chỉ chứa trạng thái `from`/`to`. `UpdateProductStatusUseCase` chuyển nó thành exception ở Application có `CatalogErrorCode.INVALID_PRODUCT_STATUS_TRANSITION`; Presentation mới tạo HTTP response.

## 2. Chuẩn hóa hệ thống kế thừa Exception trong dự án đích

1. **Kiểm kê trước khi đổi:** Liệt kê toàn bộ exception đang có, nơi ném/bắt, HTTP status thực tế và định dạng response. Đánh dấu exception nghiệp vụ, lỗi từ thư viện/hạ tầng, lỗi lập trình và các `catch (Exception)` hiện hữu. Giữ lại các contract API đã công bố hoặc lập kế hoạch chuyển đổi tương thích.
2. **Tạo một lớp gốc cho lỗi ứng dụng có thể dự kiến:** Theo mẫu `BaseException extends RuntimeException`, chứa mã lỗi và thông tin cần thiết để Presentation ánh xạ. Cho phép message mặc định từ mã và message theo ngữ cảnh. Nếu dự án đích không dùng HTTP, thay `HttpStatus` bằng phân loại trung lập rồi ánh xạ tại adapter HTTP.
3. **Thêm lớp trung gian theo ý nghĩa lỗi:** Chẳng hạn `InvalidInputException`, `ResourceNotFoundException`, `ConflictException`. Lớp exception cố định mã; mã là nguồn của HTTP status, tránh truyền status lặp lại khi tạo lỗi. Chỉ thêm nhóm mới khi nhiều lỗi cùng chia sẻ một cách xử lý; không tạo một lớp cho mỗi mã lỗi nếu không có ích cho việc bắt hoặc diễn đạt nghiệp vụ.
4. **Tạo exception nghiệp vụ theo ngữ cảnh:** Ví dụ `OrderNotFoundException extends ResourceNotFoundException` cố định `OrderErrorCode.ORDER_NOT_FOUND` trong constructor của chính nó. Caller chỉ truyền dữ liệu ngữ cảnh cần thiết. Giữ tên lớp đủ rõ để người đọc hiểu tình huống; message có thể chứa ngữ cảnh an toàn như ID, còn client dùng `code` để xử lý ổn định.
5. **Xử lý tại biên hệ thống:** Một `@RestControllerAdvice` nhận lớp gốc theo đa hình; thêm handler riêng cho lỗi request của framework. Fallback `Exception` ghi log chi tiết phía server và chỉ gửi thông điệp an toàn cho client. Không bắt exception rồi trả thành công giả hoặc làm mất stack trace.
6. **Giữ Domain độc lập:** Domain ném lỗi biểu đạt bất biến/quy tắc. Application chuyển lỗi đó thành lỗi use case có mã và ý nghĩa trình bày khi đi qua biên; tránh đưa `HttpStatus`, DTO response hay `@ExceptionHandler` vào Domain.

Sơ đồ kế thừa mẫu cho dự án đích:

```text
RuntimeException
└── BaseException (abstract; ErrorCode, trạng thái ánh xạ)
    ├── InvalidInputException (400)
    │   └── InvalidOrderStateException
    ├── ResourceNotFoundException (404)
    │   └── OrderNotFoundException
    └── ConflictException (409)
        └── DuplicateOrderReferenceException
```

## 3. Chuẩn hóa Error Code trong dự án đích

- Dùng một contract chung kiểu `ErrorCode` với mã máy đọc được và thông điệp mặc định. Mã lỗi phải **duy nhất và ổn định** trong phạm vi API; không dùng message làm khóa logic phía client.
- Chia mã dùng chung và mã theo module. Mẫu NovaCart là `VAL_001`, `SYS_003`, `CAT_003`, `PROD_004`; dự án đích tự chọn prefix phù hợp domain của mình. Lập bảng đăng ký mã gồm: mã, tên, ý nghĩa, HTTP status, module sở hữu và trạng thái sử dụng.
- Mỗi tình huống client cần phân biệt phải có mã riêng. Không tái sử dụng cùng một mã cho hai ý nghĩa khác nhau, không đổi ý nghĩa mã đã công bố và không tái cấp mã đã bỏ. Khi đổi contract, có chính sách phiên bản hoặc lộ trình tương thích.
- Phân biệt **mã** với **HTTP status**: status mô tả loại kết quả giao thức; mã cho biết nguyên nhân cụ thể. `ORDER_NOT_FOUND` và `CUSTOMER_NOT_FOUND` có thể cùng 404 nhưng khác mã.
- Đặt thông điệp mặc định ngắn, an toàn; thông điệp theo ngữ cảnh không tiết lộ truy vấn, stack trace, bí mật hay dữ liệu nhạy cảm. Kiểm tra cả message xuất phát từ framework trước khi đưa ra ngoài.
- Tập trung việc tạo error response trong handler/adapter. Ví dụ schema NovaCart: `success=false`, `data=null`, `error={status, code, message, path, fieldErrors}`, `timestamp`. Dự án đích có thể giữ schema hiện có nếu client đang phụ thuộc vào nó, miễn mọi đường lỗi dùng nhất quán.

## 4. Checked Exception và Unchecked Exception

**Thực trạng mẫu:** `BaseException` của NovaCart kế thừa `RuntimeException`, nên các lỗi nghiệp vụ qua hệ thống này là **unchecked**. Exception Domain trong ví dụ chuyển trạng thái cũng là `RuntimeException` thuần. Tài liệu này không ngụ ý NovaCart có một nhánh `BaseCheckedException`.

| Loại | Quy tắc áp dụng cho dự án đích | Cách xử lý |
|---|---|---|
| Unchecked (`RuntimeException`) | Lỗi nghiệp vụ/validation dự kiến trong use case, hoặc lỗi lập trình không thể phục hồi tại vị trí gọi | Lỗi nghiệp vụ đi qua hệ phân cấp `BaseException`; lỗi không dự kiến tới fallback 500. Bắt ở nơi có thể thêm ngữ cảnh, phục hồi hoặc chuyển đổi có chủ đích. |
| Checked (`Exception` nhưng không phải `RuntimeException`) | Hoạt động có khả năng thất bại và người gọi thực sự có phương án xử lý, ví dụ một số thao tác file hoặc tích hợp theo API thư viện | Giữ `throws` khi caller cần chọn cách phục hồi/retry; hoặc bắt tại biên hạ tầng rồi chuyển thành exception ứng dụng phù hợp, kèm `cause`. Không biến mọi checked exception thành lỗi 400/409. |

Quy tắc chuyển đổi: **quyết định theo khả năng xử lý của caller, không chỉ theo cú pháp Java**. Không tạo hai hệ phân cấp checked và unchecked song song cho cùng một lỗi nghiệp vụ. Khi bọc ngoại lệ, giữ nguyên nguyên nhân (`cause`) để điều tra; quyết định mã/status theo ý nghĩa tại biên ứng dụng. Với giao dịch Spring, xem xét quy tắc rollback cho checked exception nếu dự án đích để chúng thoát khỏi phương thức giao dịch; mặc định rollback của Spring thường áp dụng cho `RuntimeException`/`Error`, còn checked exception cần cấu hình hoặc chuyển đổi phù hợp.

## 5. Trình tự áp dụng và tiêu chí hoàn tất cho dự án đích

1. Chốt schema lỗi và danh mục mã từ API hiện tại; xác định mã nào phải giữ để không làm hỏng client.
2. Tạo `ErrorCode`, mã toàn cục và mã theo module; rà soát trùng mã bằng kiểm tra tự động.
3. Tạo lớp gốc và các nhóm exception; chuyển từng luồng nghiệp vụ sang exception cụ thể.
4. Đưa quy tắc Domain về exception thuần; chuyển đổi ở Application; gom HTTP mapping tại Presentation.
5. Viết handler riêng cho validation/framework và fallback 500; bảo đảm đường lỗi nào cũng có mã, HTTP status và body nhất quán.
6. Kiểm tra bằng controller/integration test: mã và status cho not found/conflict/validation, `fieldErrors`, lỗi Domain được chuyển đổi, lỗi không dự kiến không lộ chi tiết; kiểm tra log còn `cause` để truy vết.

## 6. Những điểm cần cải thiện khi dùng nền tảng này cho dự án khác

Các mục sau là **đề xuất tăng cường cho dự án đích**, không phải danh sách việc cần sửa trong NovaCart:

- **Tách lỗi ứng dụng khỏi Spring khi cần tái sử dụng đa adapter:** Mẫu hiện tại giữ `HttpStatus` trong `BaseException`. Dự án có thêm CLI, message consumer hoặc gRPC nên giữ phân loại lỗi trung lập và ánh xạ HTTP tại Presentation.
- **Giữ chuỗi nguyên nhân đầy đủ:** Bổ sung constructor nhận `cause` cho lớp gốc và các lớp cần bọc lỗi hạ tầng; vẫn chỉ công bố message an toàn. Mẫu hiện tại có constructor message nhưng chưa có constructor `cause`.
- **Quản trị mã lỗi bằng kiểm tra tự động:** Kiểm tra tính duy nhất, quy tắc prefix, mã không bị tái sử dụng và mapping status. Công bố bảng mã cho client và đánh dấu mã ngừng dùng.
- **Chuẩn hóa an toàn dữ liệu và quan sát:** Rà soát message có thể chứa dữ liệu do người dùng nhập; thêm correlation/request ID vào log và response nếu hệ thống cần truy vết, cùng quy tắc che dữ liệu nhạy cảm.
- **Bao phủ các lỗi framework đang hiện diện ở dự án đích:** Bổ sung handler có chủ đích cho các trường hợp như thiếu tham số, media type không hỗ trợ, lỗi xác thực/phân quyền hoặc ràng buộc dữ liệu. Chỉ ánh xạ khi hiểu ý nghĩa; fallback 500 không nên biến mọi lỗi hạ tầng thành lỗi nghiệp vụ.
- **Bảo vệ tương thích khi chuyển đổi:** Thêm contract test cho response và mã lỗi quan trọng; triển khai theo module, theo dõi lỗi chưa được phân loại, rồi loại bỏ các handler cũ khi toàn bộ client đã chuyển sang contract mới.

## 7. Áp dụng vào cấu trúc hiện tại của Moodly

Moodly giữ envelope `ApiResponse` và `ApiError(status, code, message, path, errors, requestId)`. `RequestIdFilter` phát `X-Request-ID`; `MoodlyTime` tính ngày theo `Asia/Ho_Chi_Minh`. `shared/error/exception/ApplicationException` là gốc cho lỗi ứng dụng dự kiến, chứa `ErrorCode`, message an toàn và cause tùy chọn; HTTP status được suy ra từ `ErrorCode`. `GlobalExceptionHandler` bắt lớp gốc theo đa hình, lỗi request của framework và fallback 500. Lỗi xác thực ở filter Spring Security xảy ra trước MVC nên `SecurityConfiguration` ghi cùng envelope; lỗi security phát sinh trong controller được Global ánh xạ 401/403. Job, CDC listener và lỗi trước khi MVC dispatch không thể được Global bắt; chúng cần retry, dead letter, health hoặc log ở biên riêng.

### Quy tắc tạo và ném exception trong Moodly

- `GlobalErrorCode` là nguồn duy nhất của `code`, thông điệp mặc định và HTTP status. Khi thêm mã, chọn status tại enum và kiểm tra mã không trùng. `GlobalExceptionHandler`, Spring Security và ví dụ OpenAPI đều đọc status từ mã này.
- Tại nơi `throw`, chỉ truyền **dữ liệu thay đổi theo lần gọi**: `new InvalidRequestException("The 'page' parameter must be at least 0.")` cho lỗi request đơn giản; `new ResourceNotFoundException("Habit was not found.")` cho 404; `new ConflictException()` cho version cũ; `new AvatarServiceUnavailableException(cause)` khi hạ tầng thất bại. Không truyền lại `HttpStatus` hoặc ghép `ErrorCode` và status thủ công.
- Khi một loại lỗi có nhiều tình huống hoặc nhiều mã, dùng factory có tên để cố định mã và thông điệp an toàn: `AvatarException.uploadExpired()`, `SearchInfrastructureUnavailableException.duringSearch(cause)`. Constructor nhận `(code, message)` của chúng là private; caller không phải tự chọn bộ tham số. Thêm factory mới khi có tình huống mới, không mở constructor đa dụng ở call site.
- Giữ `cause` khi chuyển IOException/lỗi tích hợp sang exception ứng dụng; không đưa message từ thư viện, truy vấn, token hay thông tin nội bộ vào response. Với lỗi bất ngờ, để fallback 500 của Global xử lý ở MVC. Ở job/listener, bắt để retry, dead letter hoặc ghi health, vì Global không nằm trên luồng đó.
- Ví dụ `ProductVersionConflictException` và `InvalidProductStatusTransitionException` ở dự án cũ chỉ minh họa hai cách biểu diễn lỗi. Moodly không sao chép chúng: nếu có quy tắc Domain độc lập mới, Domain có thể ném `RuntimeException` thuần mang trạng thái, rồi Application chuyển thành lỗi có mã trước khi ra HTTP.

| Thứ tự | Kiểm kê và quy ước Moodly |
|---|---|
| `shared` | Bean Validation → 400 `VALIDATION_FAILED`; body/parameter sai → 400 `INVALID_REQUEST`; method → 405 `METHOD_NOT_ALLOWED`; `Accept` sai → 406 `NOT_ACCEPTABLE`; request media type sai → 415 `UNSUPPORTED_MEDIA_TYPE`; duplicate key/optimistic lock → 409; fallback → 500 `INTERNAL_SERVER_ERROR`. Không ánh xạ mọi `IllegalArgumentException` thành 400 vì lỗi đó cũng có thể xuất phát từ thư viện, cấu hình hoặc lỗi lập trình. |
| `auth` | `CurrentUser` lấy identity từ JWT đã xác thực; 401/403 do security xử lý. Avatar giữ mã cũ; asset 404 là `AVATAR_UPLOAD_NOT_FOUND` 400; metadata sai là `AVATAR_ASSET_INVALID` 400; Cloudinary lỗi là `AVATAR_SERVICE_UNAVAILABLE` 503 và giữ cause. Cleanup có retry riêng sau khi profile đã lưu. |
| `habits` | Status lọc sai → 400 `INVALID_REQUEST`; habit không thuộc user → 404 `NOT_FOUND`; version cũ → 409 `CONFLICT`. DTO vẫn dùng Bean Validation. |
| `entries` | Date range, ngày tương lai và pagination sai → 400 `INVALID_REQUEST`; mood và habit log dùng validation DTO. Cần xác minh quyền sở hữu habit trước khi thêm habit log để tránh dữ liệu trỏ tới habit của user khác. |
| `stats` | Period ngoài `week` → 400 `INVALID_REQUEST` với message cố định. Lỗi truy vấn/tính toán không được che thành lỗi request. |
| `dashboard` | Cho lỗi từ habits, entries và stats truyền lên handler; không trả dashboard thiếu dữ liệu như thành công. |
| `search` | Query/date/result window sai → 400. IOException hoặc ElasticsearchException tại biên query → 503 `SEARCH_UNAVAILABLE`; giữ cause, không công bố truy vấn nội bộ. |
| `cdc` | Retry lỗi Elasticsearch tạm thời; lỗi cố định vào dead letter. Replay thất bại phải giữ dead letter và báo lỗi. Reindex IOException/ElasticsearchException → 503. Listener ghi health khi phục hồi thất bại. |
| `e2e` | Reset chỉ bật ngoài production, key sai → 403; chỉ tác động synthetic user. Lỗi Elasticsearch và xác minh chỉ mục → 503; lỗi Mongo → fallback 500. |

### Danh mục mã đã dùng

| Status | Mã |
|---|---|
| 400 | `VALIDATION_FAILED`, `INVALID_REQUEST`, `MISSING_REQUIRED_HEADER`, `AVATAR_CONTENT_TYPE_UNSUPPORTED`, `AVATAR_FILE_TOO_LARGE`, `AVATAR_UPLOAD_NOT_FOUND`, `AVATAR_ASSET_INVALID` |
| 401/403 | `UNAUTHORIZED`, `FORBIDDEN` |
| 404 | `NOT_FOUND` |
| 405/406/415 | `METHOD_NOT_ALLOWED`, `NOT_ACCEPTABLE`, `UNSUPPORTED_MEDIA_TYPE` |
| 409 | `DUPLICATE_RESOURCE`, `CONFLICT` |
| 503 | `SEARCH_UNAVAILABLE`, `AVATAR_SERVICE_UNAVAILABLE` |
| 500 | `INTERNAL_SERVER_ERROR` |

Mã đã công bố phải ổn định và duy nhất; không dùng message làm khóa. Chỉ thêm mã theo module nếu client cần phân biệt tình huống. Lỗi nghiệp vụ dự kiến là unchecked; IOException ở writer/repository vẫn là checked và được chuyển đổi tại biên. Khi bắt `InterruptedException`, khôi phục cờ interrupt. Test cần xác nhận status/code/envelope/requestId, an toàn message, retry/dead letter và không biến `IllegalArgumentException` bất ngờ thành 400.
