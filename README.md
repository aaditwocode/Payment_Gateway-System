# Payment Gateway System

Hey there! This is a solid payment gateway system built by our team in Java, following SOLID principles to make it easy to handle different payment methods. It's all about clean code, being able to add new stuff without breaking old parts, and showing how real software should be designed. Now with file-based persistence for better data management!

## Features

Here's what this system can do:

- Support for multiple payment methods like Card, UPI, Bank Transfer, and Net Banking
- Refund functionality for payments that support it
- Recurring payments that you can schedule and let run automatically
- Transaction management with file-based storage and detailed tracking
- Payer data persistence to files for reuse across sessions
- Recurring transaction persistence to maintain schedules
- Validation and error handling to keep things safe
- An interactive demo through the console so you can test everything
- Extensible design - adding new payment methods is a breeze

## Architecture

I designed this with SOLID principles in mind to ensure the code is maintainable, extensible, and robust. Here's how each principle is applied:

### Single Responsibility Principle (SRP)
Each class and interface has a single, well-defined responsibility:
- `PaymentService` is solely responsible for orchestrating payment executions, validations, and registering payment methods.
- `CardPayment` handles only card payment logic, including refunds.
- `FileTxStore` manages only transaction storage to files.
- `PayReqValidator` validates payment requests without handling other concerns.
- `PayerManager` handles saving and loading payer data.
- `RecurringManager` manages persistence of recurring transactions.
- `Scheduler` focuses on scheduling and processing recurring payments.

### Open/Closed Principle (OCP)
The system is open for extension but closed for modification:
- New payment methods can be added by implementing `IPayment` (and optionally `IRefund`) without altering existing code.
- The `PaymentService` allows registration of new payment methods dynamically.
- Persistence managers can be extended for different storage types (e.g., database) without changing core logic.

### Liskov Substitution Principle (LSP)
Subtypes can be substituted for their base types without affecting correctness:
- All implementations of `IPayment` (e.g., `CardPayment`, `UpiPayment`, `BankTransfer`) can be used interchangeably wherever `IPayment` is expected.
- `IRefund` implementations follow the same contract, ensuring that any refund-capable payment method behaves consistently.
- Persistence classes like `FileTxStore` and managers adhere to their interfaces.

### Interface Segregation Principle (ISP)
Clients are not forced to depend on interfaces they don't use:
- `IPayment` defines only payment functionality, while `IRefund` is separate for refund operations.
- `ITransactionStore` focuses solely on storage operations, not validation or logging.
- `IValidator<T>` is generic and specific to validation, avoiding bloated interfaces.
- Managers like `PayerManager` have focused interfaces for their operations.

### Dependency Inversion Principle (DIP)
High-level modules depend on abstractions, not concretions:
- `PaymentService` depends on `IValidator<PayReq>` and `ITransactionStore` interfaces, not their concrete implementations.
- `CardPayment` depends on `ITransactionStore` and `IdGen` abstractions.
- Managers depend on file I/O abstractions, allowing easy testing or replacement.
- This is achieved through constructor injection, allowing easy swapping of implementations (e.g., replacing file storage with a database).

### Key Components

- Core DTOs: Money, Payer, PayReq, PayResp
- Payment Methods: Various classes implementing the IPayment interface
- Services: PaymentService handles the main logic, Scheduler manages recurring payments
- Storage: ITransactionStore interface with a file-based implementation (FileTxStore)
- Validation: IValidator interface for checking requests
- Persistence Managers: PayerManager for payer data, RecurringManager for recurring transactions

## Technologies Used

- Java (no external libraries needed)
- Collections Framework for data structures
- Date/Time API for timestamps
- Scanner for user input in the demo
- File I/O for persistence (PrintWriter, BufferedReader)

## Prerequisites

You'll need:
- Java 8 or higher
- A command line to run it

## Getting Started

1. Clone the repo:
   ```bash
   git clone https://github.com/aaditwocode/Payment_Gateway-System.git
   cd Payment_Gateway-System
   ```

2. Compile it:
   ```bash
   javac Main.java
   ```

3. Run it:
   ```bash
   java Main
   ```

## Usage

The app has an interactive menu where you can:

1. Make a Payment - Process payments with any method
2. Refund a Transaction - Get your money back for supported methods
3. View Transaction Details - Look up info by transaction ID
4. Schedule Recurring Payment - Set up automatic payments
5. Process Recurring Payments - Run the scheduled ones
6. Generate Report - See some stats
7. Exit - Close it down

### Quick Example

- Start the app (payers are loaded from payers.txt if available)
- Add additional payers if needed (saved to payers.txt)
- Pick payment methods like card, upi, banktransfer, netbank
- Make payments, check transactions, or set up recurring ones (saved to recurring.txt)
- Generate a report to see what's been happening

## Payment Methods

| Method | What it does | Refunds? |
|--------|-------------|----------|
| Card | Credit/Debit card payments | Yes |
| UPI | Unified Payments Interface | Yes |
| Bank Transfer | Direct bank transfers | Yes |
| Net Banking | Online banking | No |

## Extending the System

Want to add a new payment method? It's easy:

1. Implement the IPayment interface (and IRefund if it supports refunds)
2. Register it in the PaymentService
3. Done - the system picks it up automatically

Example:
```java
class NewPayment implements IPayment {
    // Your code here
}

// In the main method:
svc.register("newmethod", new NewPayment());
```

## Persistence

- Transactions are saved to transactions.txt
- Payers are saved to payers.txt
- Recurring transactions are saved to recurring.txt
- All data persists across runs for continuity

## Testing

For simplicity, all payment methods are set to always succeed in this implementation.

## Future Ideas

Some things I could add later:
- Database for permanent storage
- REST API for web use
- Security features like authentication
- Support for multiple currencies
- Real payment gateway connections
- A web interface

## Contributing

Feel free to contribute:
1. Fork the repo
2. Make a feature branch
3. Commit your changes
4. Push and make a pull request

## License

This is under MIT License - check LICENSE for details.

## Authors

- Vasu Tayal
- Aditya Pratap Singh
- Prakhar Singhal
- Kush Kansal

## Thanks

Inspired by actual payment systems, this shows good software design and is great for learning.

---

Built with Java and SOLID principles
