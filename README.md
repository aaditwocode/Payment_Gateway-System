# Payment Gateway System

Hey there! This is a solid payment gateway system I built in Java, following SOLID principles to make it easy to handle different payment methods. It's all about clean code, being able to add new stuff without breaking old parts, and showing how real software should be designed.

## Features

Here's what this system can do:

- Support for multiple payment methods like Card, UPI, Wallet, Bank Transfer, Crypto, and Net Banking
- Refund functionality for payments that support it
- Recurring payments that you can schedule and let run automatically
- Transaction management with in-memory storage and detailed tracking
- Validation and error handling to keep things safe
- A logging system for keeping an eye on what's happening
- An interactive demo through the console so you can test everything
- Extensible design - adding new payment methods is a breeze

## Architecture

I designed this with SOLID principles in mind:

- Single Responsibility: Each class does one thing well
- Open/Closed: You can add new payment methods without touching existing code
- Liskov Substitution: All payment implementations work interchangeably
- Interface Segregation: Different interfaces for different jobs
- Dependency Inversion: High-level stuff doesn't rely on low-level details

### Key Components

- Core DTOs: Money, Payer, PayReq, PayResp
- Payment Methods: Various classes implementing the IPayment interface
- Services: PaymentService handles the main logic, Scheduler manages recurring payments
- Storage: ITransactionStore interface with an in-memory implementation
- Validation: IValidator interface for checking requests
- Logging: ILogger interface with a console logger

## Technologies Used

- Java (no external libraries needed)
- Collections Framework for data structures
- Date/Time API for timestamps
- Scanner for user input in the demo

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

- Start the app
- Add some payers with their info and wallet balances
- Pick payment methods like card, upi, wallet, etc.
- Make payments, check transactions, or set up recurring ones
- Generate a report to see what's been happening

## Payment Methods

| Method | What it does | Refunds? |
|--------|-------------|----------|
| Card | Credit/Debit card payments | Yes |
| UPI | Unified Payments Interface | Yes |
| Wallet | Digital wallet payments | No |
| Bank Transfer | Direct bank transfers | Yes |
| Crypto | Cryptocurrency payments | No |
| Net Banking | Online banking | No |

## Extending the System

Want to add a new payment method? It's easy:

1. Implement the IPayment interface (and IRefund if it supports refunds)
2. Register it in the PayFactory
3. Done - the system picks it up automatically

Example:
```java
class NewPayment implements IPayment {
    // Your code here
}

// In the main method:
fact.register("newmethod", new NewPayment());
```

## Testing

The system simulates different outcomes for testing:
- Card payments work for even amounts
- UPI fails for amounts divisible by 5
- Bank transfers fail for amounts divisible by 3
- Crypto fails for amounts divisible by 7

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

## Author

Aadit Wocode - GitHub: aaditwocode

## Thanks

Inspired by actual payment systems, this shows good software design and is great for learning.

---

Built with Java and SOLID principles
