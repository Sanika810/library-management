# Library Management System

Spring Boot 3.3 / Java 17 / Spring Data JPA (MySQL) / Thymeleaf / Spring Security, with a fine and **simulated** payment module.

## Run (IntelliJ IDEA)
1. Install JDK 17+ and MySQL 8. File > Open > select this folder (the `pom.xml`); let Maven import finish.
2. Settings > Build, Execution, Deployment > Compiler > Annotation Processors > enable annotation processing (Lombok).
3. Set your MySQL password: edit `src/main/resources/application.properties` (`spring.datasource.password`), or set the
   environment variables `DB_USER` / `DB_PASSWORD` in Run > Edit Configurations. The database `library_db` is created automatically.
4. Run `LibraryApplication`. Open http://localhost:8080.
5. Seeded librarian: `librarian@library.com` / `Librarian@123`. Members register at `/register`.

The seeded librarian credentials are for local development only. Before deploying, set a strong
`APP_LIBRARIAN_PASSWORD` environment variable and do not expose the default account publicly.

## Business rules
- Loan period 14 days. Fine = overdue days x rate (default 5.00/day, librarian-editable on the Fee Dashboard).
  Overdue days = `ChronoUnit.DAYS.between(dueDate, returnDate)` on `LocalDate`, never negative. Due 15 Oct, returned 19 Oct = 4 days = 20.00.
- One fine per borrow record (unique constraint + row lock on the borrow record at return time).
- Outstanding = original fine - waived - SUCCESS payments (calculated, not stored). FAILED/PENDING payments never count.
- Payments lock the fine row (`SELECT ... FOR UPDATE`) as the first statement of one transaction, so concurrent payments cannot overpay.
  A per-form `idempotency_key` (unique) prevents the same submission being processed twice.
- Amount, user, status and transaction result are never trusted from the browser. The payment processor is a SIMULATION
  (`SimulatedPaymentProcessor`, failure rate `app.payment.simulated.failure-rate`); replace the `PaymentProcessor` implementation for a real gateway.
- Waivers (`FineAdjustment`) need a librarian, a reason and an amount <= outstanding; they are permanent audit rows. Financial rows are never deleted.

## Try the fine flow manually
1. Librarian: add a book. Member: register, borrow the book.
2. Make the loan overdue in MySQL: `UPDATE library_db.borrow_records SET due_date = CURDATE() - INTERVAL 4 DAY WHERE id = 1;`
3. Member: return the book -> fine of 20.00. Open My Fines > Pay.
4. Librarian: Fines (search/filter, waive), Payments, Fee Dashboard.

## Tests
Run all: right-click `src/test/java` > Run 'All Tests' (or `mvn test`). Tests use an in-memory H2 database, no MySQL needed.
`FineCalculationTest` (date maths), `BorrowReturnTest` (return/fine/duplicate return), `PaymentServiceTest` (payments, failures, overpayment,
idempotency, concurrency, adjustments, dashboard totals), `SecurityAccessTest` (roles, ownership, CSRF, forged fields).

## Packages
`config` security + seed data, `entity`, `repository`, `service` (business logic and transactions), `payment` (processor interface + simulation),
`controller`, `dto`, `exception`. Templates in `src/main/resources/templates/{auth,user,librarian,fragments}`.
