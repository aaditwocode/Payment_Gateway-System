import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

// core DTOs
class Money {
    private final long amount; // in paise
    private final String currency;

    public Money(long amount, String currency) {
        this.amount = amount;
        this.currency = currency;
    }

    public long getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public Money add(Money m) {
        if (!m.currency.equals(this.currency))
            throw new IllegalArgumentException("curr mismatch");
        return new Money(this.amount + m.amount, currency);
    }

    public Money sub(Money m) {
        if (!m.currency.equals(this.currency))
            throw new IllegalArgumentException("curr mismatch");
        return new Money(this.amount - m.amount, currency);
    }

    @Override
    public String toString() {
        return String.format("%s %.2f", currency, amount / 100.0);
    }
}

class Payer {
    private final String id;
    private final String name;

    public Payer(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}

class PayReq {
    private final Payer payer;
    private final Money amt;
    private final Map<String, String> meta;

    public PayReq(Payer p, Money amt, Map<String, String> meta) {
        this.payer = p;
        this.amt = amt;
        this.meta = meta == null ? Collections.emptyMap() : new HashMap<>(meta);
    }

    public Payer getPayer() {
        return payer;
    }

    public Money getAmt() {
        return amt;
    }

    public Map<String, String> getMeta() {
        return meta;
    }
}

class PayResp {
    private final String txId;
    private final boolean ok;
    private final String msg;

    public PayResp(String txId, boolean ok, String msg) {
        this.txId = txId;
        this.ok = ok;
        this.msg = msg;
    }

    public String getTxId() {
        return txId;
    }

    public boolean isOk() {
        return ok;
    }

    public String getMsg() {
        return msg;
    }

    @Override
    public String toString() {
        return "txId=" + txId + " ok=" + ok + " msg=" + msg;
    }
}

// Interfaces - SRP + ISP
interface IPayment {
    PayResp pay(PayReq req);
}

interface IRefund {
    PayResp refund(String txId, Money amt);
}

interface ITransactionStore {
    void save(Transaction t);

    Optional<Transaction> find(String txId);
}

// Transaction entity
class Transaction {
    public enum Status {
        PENDING, SUCCESS, FAILED, REFUNDED
    }

    private final String id;
    private final String method;
    private final Payer payer;
    private final Money amt;
    private Status status;
    private final Date ts;
    private Date updated;

    public Transaction(String id, String method, Payer p, Money amt, Status st) {
        this.id = id;
        this.method = method;
        this.payer = p;
        this.amt = amt;
        this.status = st;
        this.ts = new Date();
        this.updated = ts;
    }

    public String getId() {
        return id;
    }

    public String getMethod() {
        return method;
    }

    public Payer getPayer() {
        return payer;
    }

    public Money getAmt() {
        return amt;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status s) {
        this.status = s;
        this.updated = new Date();
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "id='" + id + '\'' +
                ", method='" + method + '\'' +
                ", payer=" + payer.getName() +
                ", amt=" + amt +
                ", status=" + status +
                ", ts=" + ts +
                ", updated=" + updated +
                '}';
    }
}

// Simple in-memory store - Dependency Inversion (abstraction)
class MemTxStore implements ITransactionStore {
    private final Map<String, Transaction> map = new HashMap<>();

    @Override
    public void save(Transaction t) {
        map.put(t.getId(), t);
    }

    @Override
    public Optional<Transaction> find(String txId) {
        return Optional.ofNullable(map.get(txId));
    }
}

// Logger / Observer - single responsibility
interface ILogger {
    void info(String s);

    void err(String s);
}

class ConsoleLogger implements ILogger {
    @Override
    public void info(String s) {
        System.out.println("[INFO] " + s);
    }

    @Override
    public void err(String s) {
        System.err.println("[ERR] " + s);
    }
}

// Unique id generator
class IdGen {
    private final AtomicLong c = new AtomicLong(1000);

    public String next(String prefix) {
        return prefix + c.getAndIncrement();
    }
}

// Validators
interface IValidator<T> {
    void validate(T t) throws ValidationException;
}

class PayReqValidator implements IValidator<PayReq> {
    @Override
    public void validate(PayReq r) throws ValidationException {
        if (r.getAmt() == null)
            throw new ValidationException("Amount is null");
        if (r.getAmt().getAmount() <= 0)
            throw new ValidationException("Amount must be positive");
        if (r.getPayer() == null)
            throw new ValidationException("Payer is null");
    }
}

// Payment methods implementations - Open/Closed: add new impl without changing
// others
class CardPayment implements IPayment, IRefund {
    private final ITransactionStore store;
    private final ILogger log;
    private final IdGen idg;

    public CardPayment(ITransactionStore s, ILogger l, IdGen idg) {
        this.store = s;
        this.log = l;
        this.idg = idg;
    }

    @Override
    public PayResp pay(PayReq req) {
        String tx = idg.next("CARD-");
        Transaction t = new Transaction(tx, "CARD", req.getPayer(), req.getAmt(), Transaction.Status.PENDING);
        store.save(t);
        boolean ok = simulateNetwork(req);
        if (ok) {
            t.setStatus(Transaction.Status.SUCCESS);
            store.save(t);
            log.info("card pay ok " + tx);
            return new PayResp(tx, true, "card success");
        } else {
            t.setStatus(Transaction.Status.FAILED);
            store.save(t);
            log.err("card fail " + tx);
            return new PayResp(tx, false, "card failed");
        }
    }

    @Override
    public PayResp refund(String txId, Money amt) {
        Optional<Transaction> o = store.find(txId);
        if (!o.isPresent())
            return new PayResp("", false, "tx not found");
        Transaction t = o.get();
        if (!t.getMethod().equals("CARD"))
            return new PayResp(txId, false, "method mismatch");
        boolean ok = simulateRefund(amt);
        if (ok) {
            Transaction r = new Transaction(idg.next("R-"), "CARD-REFUND", t.getPayer(), amt,
                    Transaction.Status.REFUNDED);
            store.save(r);
            t.setStatus(Transaction.Status.REFUNDED);
            store.save(t);
            log.info("card refund ok " + txId);
            return new PayResp(r.getId(), true, "refund success");
        } else {
            log.err("card refund fail " + txId);
            return new PayResp("", false, "refund failed");
        }
    }

    private boolean simulateNetwork(PayReq r) {
        return (r.getAmt().getAmount() % 2) == 0;
    }

    private boolean simulateRefund(Money amt) {
        return amt.getAmount() > 0;
    }
}

class UpiPayment implements IPayment, IRefund {
    private final ITransactionStore s;
    private final ILogger l;
    private final IdGen g;

    public UpiPayment(ITransactionStore s, ILogger l, IdGen g) {
        this.s = s;
        this.l = l;
        this.g = g;
    }

    @Override
    public PayResp pay(PayReq r) {
        String tx = g.next("UPI-");
        Transaction t = new Transaction(tx, "UPI", r.getPayer(), r.getAmt(), Transaction.Status.PENDING);
        s.save(t);
        boolean ok = sendToBank(r);
        if (ok) {
            t.setStatus(Transaction.Status.SUCCESS);
            s.save(t);
            l.info("upi ok " + tx);
            return new PayResp(tx, true, "upi success");
        } else {
            t.setStatus(Transaction.Status.FAILED);
            s.save(t);
            l.err("upi fail " + tx);
            return new PayResp(tx, false, "upi failed");
        }
    }

    @Override
    public PayResp refund(String txId, Money amt) {
        Optional<Transaction> o = s.find(txId);
        if (!o.isPresent())
            return new PayResp("", false, "tx not found");
        Transaction t = o.get();
        if (!t.getMethod().equals("UPI"))
            return new PayResp(txId, false, "method mismatch");
        boolean ok = reverse(amt);
        if (ok) {
            Transaction r = new Transaction(g.next("R-"), "UPI-REFUND", t.getPayer(), amt, Transaction.Status.REFUNDED);
            s.save(r);
            t.setStatus(Transaction.Status.REFUNDED);
            s.save(t);
            l.info("upi refund ok " + txId);
            return new PayResp(r.getId(), true, "refund success");
        } else {
            l.err("upi refund fail " + txId);
            return new PayResp("", false, "refund failed");
        }
    }

    private boolean sendToBank(PayReq r) {
        return r.getAmt().getAmount() % 5 != 0;
    }

    private boolean reverse(Money amt) {
        return amt.getAmount() <= 1000000;
    }
}

class WalletPayment implements IPayment {
    private final ITransactionStore s;
    private final ILogger l;
    private final IdGen g;
    private final Map<String, Long> balances = new HashMap<>();

    public WalletPayment(ITransactionStore s, ILogger l, IdGen g) {
        this.s = s;
        this.l = l;
        this.g = g;
    }

    public void credit(String uid, long amount) {
        balances.put(uid, balances.getOrDefault(uid, 0L) + amount);
    }

    @Override
    public PayResp pay(PayReq r) {
        String uid = r.getPayer().getId();
        long bal = balances.getOrDefault(uid, 0L);
        if (bal < r.getAmt().getAmount()) {
            String tx = g.next("W-");
            Transaction t = new Transaction(tx, "WALLET", r.getPayer(), r.getAmt(), Transaction.Status.FAILED);
            s.save(t);
            l.err("wallet low " + tx);
            return new PayResp(tx, false, "insufficient");
        }
        balances.put(uid, bal - r.getAmt().getAmount());
        String tx = g.next("W-");
        Transaction t = new Transaction(tx, "WALLET", r.getPayer(), r.getAmt(), Transaction.Status.SUCCESS);
        s.save(t);
        l.info("wallet ok " + tx);
        return new PayResp(tx, true, "wallet success");
    }
}

// Payment factory - Open/Closed + Dependency Injection via constructor where
// used
class PayFactory {
    private final Map<String, IPayment> map = new HashMap<>();
    private final Map<String, IRefund> refunds = new HashMap<>();

    public void register(String key, IPayment p) {
        map.put(key, p);
        if (p instanceof IRefund)
            refunds.put(key, (IRefund) p);
    }

    public IPayment get(String key) {
        return map.get(key);
    }

    public Optional<IRefund> getRefund(String key) {
        return Optional.ofNullable(refunds.get(key));
    }

    public Set<String> methods() {
        return new HashSet<>(map.keySet());
    }
}

// Service layer - Single responsibility: orchestrates payments, applies
// policies, logs
class PaymentService {
    private final PayFactory f;
    private final IValidator<PayReq> v;
    private final ILogger l;
    private final ITransactionStore s;

    public PaymentService(PayFactory f, IValidator<PayReq> v, ILogger l, ITransactionStore s) {
        this.f = f;
        this.v = v;
        this.l = l;
        this.s = s;
    }

    public PayResp execute(String method, PayReq req) {
        try {
            v.validate(req);
        } catch (ValidationException e) {
            return new PayResp("", false, e.getMessage());
        }
        IPayment p = f.get(method);
        if (p == null)
            return new PayResp("", false, "method unsupported");
        l.info("exec method " + method + " payer " + req.getPayer().getName());
        return p.pay(req);
    }

    public PayResp refund(String method, String txId, Money amt) {
        Optional<IRefund> ro = f.getRefund(method);
        if (!ro.isPresent())
            return new PayResp("", false, "refund not supported");
        IRefund r = ro.get();
        l.info("refund method " + method + " tx " + txId);
        return r.refund(txId, amt);
    }

    public Optional<Transaction> find(String txId) {
        return s.find(txId);
    }
}

class ValidationException extends Exception {
    public ValidationException(String message) {
        super(message);
    }
}

class BankTransfer implements IPayment, IRefund {
    private final ITransactionStore store;
    private final ILogger log;
    private final IdGen idg;

    public BankTransfer(ITransactionStore s, ILogger l, IdGen idg) {
        this.store = s;
        this.log = l;
        this.idg = idg;
    }

    @Override
    public PayResp pay(PayReq req) {
        String tx = idg.next("BT-");
        Transaction t = new Transaction(tx, "BANK_TRANSFER", req.getPayer(), req.getAmt(), Transaction.Status.PENDING);
        store.save(t);
        boolean ok = simulateTransfer(req);
        if (ok) {
            t.setStatus(Transaction.Status.SUCCESS);
            store.save(t);
            log.info("bank transfer ok " + tx);
            return new PayResp(tx, true, "bank transfer success");
        } else {
            t.setStatus(Transaction.Status.FAILED);
            store.save(t);
            log.err("bank transfer fail " + tx);
            return new PayResp(tx, false, "bank transfer failed");
        }
    }

    @Override
    public PayResp refund(String txId, Money amt) {
        Optional<Transaction> o = store.find(txId);
        if (!o.isPresent())
            return new PayResp("", false, "tx not found");
        Transaction t = o.get();
        if (!t.getMethod().equals("BANK_TRANSFER"))
            return new PayResp(txId, false, "method mismatch");
        boolean ok = simulateRefund(amt);
        if (ok) {
            Transaction r = new Transaction(idg.next("R-"), "BT-REFUND", t.getPayer(), amt,
                    Transaction.Status.REFUNDED);
            store.save(r);
            t.setStatus(Transaction.Status.REFUNDED);
            store.save(t);
            log.info("bank transfer refund ok " + txId);
            return new PayResp(r.getId(), true, "refund success");
        } else {
            log.err("bank transfer refund fail " + txId);
            return new PayResp("", false, "refund failed");
        }
    }

    private boolean simulateTransfer(PayReq r) {
        return r.getAmt().getAmount() % 3 != 0;
    }

    private boolean simulateRefund(Money amt) {
        return amt.getAmount() > 100;
    }
}

class CryptoPayment implements IPayment {
    private final ITransactionStore s;
    private final ILogger l;
    private final IdGen g;

    public CryptoPayment(ITransactionStore s, ILogger l, IdGen g) {
        this.s = s;
        this.l = l;
        this.g = g;
    }

    @Override
    public PayResp pay(PayReq r) {
        String tx = g.next("CRYPTO-");
        Transaction t = new Transaction(tx, "CRYPTO", r.getPayer(), r.getAmt(), Transaction.Status.PENDING);
        s.save(t);
        boolean ok = simulateCrypto(r);
        if (ok) {
            t.setStatus(Transaction.Status.SUCCESS);
            s.save(t);
            l.info("crypto ok " + tx);
            return new PayResp(tx, true, "crypto success");
        } else {
            t.setStatus(Transaction.Status.FAILED);
            s.save(t);
            l.err("crypto fail " + tx);
            return new PayResp(tx, false, "crypto failed");
        }
    }

    private boolean simulateCrypto(PayReq r) {
        return r.getAmt().getAmount() % 7 != 0;
    }
}

interface IRecurringPayment {
    void schedule(PayReq req, int intervalDays);

    void processRecurring();
}

class RecurringTransaction extends Transaction {
    private final int intervalDays;
    private Date nextRun;

    public RecurringTransaction(String id, String method, Payer p, Money amt, Status st, int interval) {
        super(id, method, p, amt, st);
        this.intervalDays = interval;
        this.nextRun = new Date(System.currentTimeMillis() + interval * 24 * 60 * 60 * 1000L);
    }

    public int getIntervalDays() {
        return intervalDays;
    }

    public Date getNextRun() {
        return nextRun;
    }

    public void updateNextRun() {
        this.nextRun = new Date(nextRun.getTime() + intervalDays * 24 * 60 * 60 * 1000L);
    }
}

class Scheduler implements IRecurringPayment {
    private final List<RecurringTransaction> recurring = new ArrayList<>();
    private final PaymentService svc;
    private final ILogger log;
    private final IdGen idg;

    public Scheduler(PaymentService svc, ILogger log, IdGen idg) {
        this.svc = svc;
        this.log = log;
        this.idg = idg;
    }

    @Override
    public void schedule(PayReq req, int intervalDays) {
        String tx = idg.next("REC-");
        RecurringTransaction rt = new RecurringTransaction(tx, "RECURRING", req.getPayer(), req.getAmt(),
                Transaction.Status.PENDING, intervalDays);
        recurring.add(rt);
        log.info("scheduled recurring " + tx);
    }

    @Override
    public void processRecurring() {
        Date now = new Date();
        for (RecurringTransaction rt : new ArrayList<>(recurring)) {
            if (rt.getNextRun().before(now) || rt.getNextRun().equals(now)) {
                PayReq pr = new PayReq(rt.getPayer(), rt.getAmt(), Collections.emptyMap());
                PayResp resp = svc.execute("card", pr); // assume card for recurring
                if (resp.isOk()) {
                    rt.updateNextRun();
                    log.info("recurring processed " + rt.getId());
                } else {
                    log.err("recurring failed " + rt.getId());
                    // remove if failed
                    recurring.remove(rt);
                }
            }
        }
    }
}

class ReportGenerator {
    public ReportGenerator() {
    }

    public void generateReport() {
        System.out.println("=== Transaction Report ===");
        System.out.println("Total transactions: unknown (in-memory store)");
        System.out.println("Successful transactions: unknown");
        System.out.println("Failed transactions: unknown");
        System.out.println("Report generated at " + new Date());
    }
}

class PaymentFacade {
    private final PaymentService svc;
    private final Scheduler sch;
    private final ReportGenerator rg;

    public PaymentFacade(PaymentService svc, Scheduler sch, ReportGenerator rg) {
        this.svc = svc;
        this.sch = sch;
        this.rg = rg;
    }

    public PayResp pay(String method, Payer payer, long amount, String currency) {
        Money m = new Money(amount, currency);
        PayReq req = new PayReq(payer, m, Collections.emptyMap());
        return svc.execute(method, req);
    }

    public void scheduleRecurring(Payer payer, long amount, String currency, int interval) {
        Money m = new Money(amount, currency);
        PayReq req = new PayReq(payer, m, Collections.emptyMap());
        sch.schedule(req, interval);
    }

    public void processRecurring() {
        sch.processRecurring();
    }

    public void generateReport() {
        rg.generateReport();
    }
}

// Demo main

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        System.out.println("==========================================");
        System.out.println("   Welcome to Payment Gateway System");
        System.out.println("   Built with SOLID Principles in Java");
        System.out.println("==========================================");
        System.out.println();

        ITransactionStore store = new MemTxStore();
        ILogger log = new ConsoleLogger();
        IdGen idg = new IdGen();
        PayFactory fact = new PayFactory();

        CardPayment card = new CardPayment(store, log, idg);
        UpiPayment upi = new UpiPayment(store, log, idg);
        WalletPayment wal = new WalletPayment(store, log, idg);
        BankTransfer bt = new BankTransfer(store, log, idg);
        CryptoPayment crypto = new CryptoPayment(store, log, idg);

        fact.register("card", card);
        fact.register("upi", upi);
        fact.register("wallet", wal);
        fact.register("banktransfer", bt);
        fact.register("crypto", crypto);

        PayReqValidator val = new PayReqValidator();
        PaymentService svc = new PaymentService(fact, val, log, store);

        Scheduler sch = new Scheduler(svc, log, idg);
        ReportGenerator rg = new ReportGenerator();
        PaymentFacade facade = new PaymentFacade(svc, sch, rg);

        List<Payer> payers = new ArrayList<>();

        System.out.println("How many payers do you want to add?");
        int numPayers = sc.nextInt();
        sc.nextLine(); // consume newline

        for (int i = 0; i < numPayers; i++) {
            System.out.println("Enter Payer ID:");
            String id = sc.nextLine();
            System.out.println("Enter Payer Name:");
            String name = sc.nextLine();
            Payer p = new Payer(id, name);
            payers.add(p);

            System.out.println("Enter initial wallet balance in paise (0 if none):");
            long balance = sc.nextLong();
            sc.nextLine();
            if (balance > 0) {
                wal.credit(id, balance);
                System.out.println("Wallet credited with " + new Money(balance, "INR"));
            }
        }

        // Add NetBanking
        IPayment nb;
        nb = new IPayment() {
            private final ITransactionStore s = store;
            private final ILogger l2 = log;
            private final IdGen g2 = idg;

            @Override
            public PayResp pay(PayReq req) {
                String tx = g2.next("NB-");
                Transaction t = new Transaction(tx, "NETBANK", req.getPayer(), req.getAmt(),
                        Transaction.Status.PENDING);
                s.save(t);
                t.setStatus(Transaction.Status.SUCCESS);
                s.save(t);
                l2.info("netbank ok " + tx);
                return new PayResp(tx, true, "netbank success");
            }
        };
        fact.register("netbank", nb);

        System.out.println("Available payment methods: card, upi, wallet, banktransfer, crypto, netbank");
        System.out.println();

        while (true) {
            System.out.println("==========================================");
            System.out.println("Menu:");
            System.out.println("1. Make a Payment");
            System.out.println("2. Refund a Transaction");
            System.out.println("3. View Transaction Details");
            System.out.println("4. Schedule Recurring Payment");
            System.out.println("5. Process Recurring Payments");
            System.out.println("6. Generate Report");
            System.out.println("7. Exit");
            System.out.println("Choose an option:");
            int choice = sc.nextInt();
            sc.nextLine();

            switch (choice) {
                case 1:
                    System.out.println("Enter payment method:");
                    String method = sc.nextLine();
                    System.out.println("Enter payer index (0 to " + (payers.size() - 1) + "):");
                    int pIdx = sc.nextInt();
                    System.out.println("Enter amount in paise:");
                    long amount = sc.nextLong();
                    sc.nextLine();
                    if (pIdx >= 0 && pIdx < payers.size()) {
                        Payer payer = payers.get(pIdx);
                        PayReq req = new PayReq(payer, new Money(amount, "INR"), Collections.emptyMap());
                        PayResp resp = svc.execute(method, req);
                        System.out.println("Payment Result: " + (resp.isOk() ? "SUCCESS" : "FAILED") + " | "
                                + resp.getMsg() + " | TxID: " + resp.getTxId());
                    } else {
                        System.out.println("Invalid payer index.");
                    }
                    break;
                case 2:
                    System.out.println("Enter payment method for refund:");
                    String refMethod = sc.nextLine();
                    System.out.println("Enter transaction ID to refund:");
                    String txId = sc.nextLine();
                    System.out.println("Enter refund amount in paise:");
                    long refAmt = sc.nextLong();
                    sc.nextLine();
                    PayResp refResp = svc.refund(refMethod, txId, new Money(refAmt, "INR"));
                    System.out.println(
                            "Refund Result: " + (refResp.isOk() ? "SUCCESS" : "FAILED") + " | " + refResp.getMsg());
                    break;
                case 3:
                    System.out.println("Enter transaction ID:");
                    String viewTxId = sc.nextLine();
                    svc.find(viewTxId).ifPresentOrElse(
                            tx -> System.out.println("Transaction: " + tx),
                            () -> System.out.println("Transaction not found."));
                    break;
                case 4:
                    System.out.println("Enter payer index:");
                    int recPIdx = sc.nextInt();
                    System.out.println("Enter amount in paise:");
                    long recAmt = sc.nextLong();
                    System.out.println("Enter interval in days:");
                    int interval = sc.nextInt();
                    sc.nextLine();
                    if (recPIdx >= 0 && recPIdx < payers.size()) {
                        Payer recPayer = payers.get(recPIdx);
                        facade.scheduleRecurring(recPayer, recAmt, "INR", interval);
                        System.out.println("Recurring payment scheduled.");
                    } else {
                        System.out.println("Invalid payer index.");
                    }
                    break;
                case 5:
                    facade.processRecurring();
                    System.out.println("Recurring payments processed.");
                    break;
                case 6:
                    facade.generateReport();
                    break;
                case 7:
                    System.out.println("Exiting...");
                    sc.close();
                    return;
                default:
                    System.out.println("Invalid choice. Try again.");
            }
            System.out.println();
        }
    }
}