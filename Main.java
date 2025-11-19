
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class Main
{
    public static void main(String[] args)
    {
        ITransactionStore store = new MemTxStore();
        ILogger log = new ConsoleLogger();
        IdGen idg = new IdGen();
        PayFactory fact = new PayFactory();

        CardPayment card = new CardPayment(store, log, idg);
        UpiPayment upi = new UpiPayment(store, log, idg);
        WalletPayment wal = new WalletPayment(store, log, idg);

        fact.register("card", card);
        fact.register("upi", upi);
        fact.register("wallet", wal);

        PayReqValidator val = new PayReqValidator();
        PaymentService svc = new PaymentService(fact, val, log, store);

        Payer p1 = new Payer("u1", "Kush");
        Payer p2 = new Payer("u2", "Riya");

        wal.credit("u1", 10000); // 100.00
        wal.credit("u2", 50000); // 500.00

        Map<String, String> m = new HashMap<>();
        m.put("order", "ord-100");

        PayReq r1 = new PayReq(p1, new Money(2500, "INR"), m);
        PayReq r2 = new PayReq(p2, new Money(12345, "INR"), m);
        PayReq r3 = new PayReq(p1, new Money(12000, "INR"), m);

        System.out.println("=== try wallet pay ===");
        PayResp a1 = svc.execute("wallet", r1);
        System.out.println(a1);

        System.out.println("=== try card pay ===");
        PayResp a2 = svc.execute("card", r2);
        System.out.println(a2);

        System.out.println("=== try upi pay ===");
        PayResp a3 = svc.execute("upi", r3);
        System.out.println(a3);

        System.out.println("=== try refund card ===");
        if (a2.isOk())
        {
            PayResp rf = svc.refund("card", a2.getTxId(), new Money(12345, "INR"));
            System.out.println(rf);
        }

        System.out.println("=== find txs ===");
        svc.find(a1.getTxId()).ifPresent(System.out::println);
        svc.find(a2.getTxId()).ifPresent(System.out::println);
        svc.find(a3.getTxId()).ifPresent(System.out::println);

        System.out.println("=== add new method easily: NetBanking ===");
        IPayment nb = new IPayment()
        {
            private final ITransactionStore s = store;
            private final ILogger l2 = log;
            private final IdGen g2 = idg;

            public PayResp pay(PayReq req)
            {
                String tx = g2.next("NB-");
                Transaction t = new Transaction(tx, "NETBANK", req.getPayer(), req.getAmt(), Transaction.Status.PENDING);
                s.save(t);
                t.setStatus(Transaction.Status.SUCCESS);
                s.save(t);
                l2.info("netbank ok " + tx);
                return new PayResp(tx, true, "netbank success");
            }
        };

        fact.register("netbank", nb);
        PayReq r4 = new PayReq(p2, new Money(20000, "INR"), null);
        PayResp a4 = svc.execute("netbank", r4);
        System.out.println(a4);
    } 
}

// --- Domain Objects ---

class Money
{
    private final long amount; // in paise
    private final String currency;

    public Money(long amount, String currency)
    {
        this.amount = amount;
        this.currency = currency;
    }

    public long getAmount()
    {
        return amount;
    }

    public String getCurrency()
    {
        return currency;
    }

    public Money add(Money m)
    {
        if (!m.currency.equals(this.currency)) throw new IllegalArgumentException("curr mismatch");
        return new Money(this.amount + m.amount, currency);
    }

    public Money sub(Money m)
    {
        if (!m.currency.equals(this.currency)) throw new IllegalArgumentException("curr mismatch");
        return new Money(this.amount - m.amount, currency);
    }

    @Override
    public String toString()
    {
        return String.format("%s %.2f", currency, amount / 100.0);
    }
}

class Payer
{
    private final String id;
    private final String name;

    public Payer(String id, String name)
    {
        this.id = id;
        this.name = name;
    }

    public String getId()
    {
        return id;
    }

    public String getName()
    {
        return name;
    }
}

class PayReq
{
    private final Payer payer;
    private final Money amt;
    private final Map<String, String> meta;

    public PayReq(Payer p, Money amt, Map<String, String> meta)
    {
        this.payer = p;
        this.amt = amt;
        this.meta = meta == null ? Collections.emptyMap() : new HashMap<>(meta);
    }

    public Payer getPayer()
    {
        return payer;
    }

    public Money getAmt()
    {
        return amt;
    }

    public Map<String, String> getMeta()
    {
        return meta;
    }
}

class PayResp
{
    private final String txId;
    private final boolean ok;
    private final String msg;

    public PayResp(String txId, boolean ok, String msg)
    {
        this.txId = txId;
        this.ok = ok;
        this.msg = msg;
    }

    public String getTxId()
    {
        return txId;
    }

    public boolean isOk()
    {
        return ok;
    }

    public String getMsg()
    {
        return msg;
    }

    @Override
    public String toString()
    {
        return "txId=" + txId + " ok=" + ok + " msg=" + msg;
    }
}

// --- Interfaces ---

interface IPayment
{
    PayResp pay(PayReq req);
}

interface IRefund
{
    PayResp refund(String txId, Money amt);
}

interface ITransactionStore
{
    void save(Transaction t);
    Optional<Transaction> find(String txId);
}

// --- Entities ---

class Transaction
{
    public enum Status {PENDING, SUCCESS, FAILED, REFUNDED}

    private final String id;
    private final String method;
    private final Payer payer;
    private final Money amt;
    private Status status;
    private final Date ts;
    private Date updated;

    public Transaction(String id, String method, Payer p, Money amt, Status st)
    {
        this.id = id;
        this.method = method;
        this.payer = p;
        this.amt = amt;
        this.status = st;
        this.ts = new Date();
        this.updated = ts;
    }

    public String getId()
    {
        return id;
    }

    public String getMethod()
    {
        return method;
    }

    public Payer getPayer()
    {
        return payer;
    }

    public Money getAmt()
    {
        return amt;
    }

    public Status getStatus()
    {
        return status;
    }

    public void setStatus(Status s)
    {
        this.status = s;
        this.updated = new Date();
    }

    @Override
    public String toString()
    {
        return "Transaction{" +
               "id='" + id + '\'' +
               ", method='" + method + '\'' +
               ", payer=" + payer.getName() +
               ", amt=" + amt +
               ", status=" + status +
               ", ts=" + ts +
               '}';
    }
}

// --- Infrastructure Implementations ---

class MemTxStore implements ITransactionStore
{
    private final Map<String, Transaction> map = new HashMap<>();

    public void save(Transaction t)
    {
        map.put(t.getId(), t);
    }

    public Optional<Transaction> find(String txId)
    {
        return Optional.ofNullable(map.get(txId));
    }
}

interface ILogger
{
    void info(String s);
    void err(String s);
}

class ConsoleLogger implements ILogger
{
    public void info(String s)
    {
        System.out.println("[INFO] " + s);
    }

    public void err(String s)
    {
        System.err.println("[ERR] " + s);
    }
}

class IdGen
{
    private final AtomicLong c = new AtomicLong(1000);

    public String next(String prefix)
    {
        return prefix + c.getAndIncrement();
    }
}

// --- Validation ---

interface IValidator<T>
{
    void validate(T t);
}

class PayReqValidator implements IValidator<PayReq>
{
    public void validate(PayReq r)
    {
        if (r.getAmt() == null) throw new IllegalArgumentException("amt null");
        if (r.getAmt().getAmount() <= 0) throw new IllegalArgumentException("amt <=0");
        if (r.getPayer() == null) throw new IllegalArgumentException("payer null");
    }
}

// --- Payment Strategies ---

class CardPayment implements IPayment, IRefund
{
    private final ITransactionStore store;
    private final ILogger log;
    private final IdGen idg;

    public CardPayment(ITransactionStore s, ILogger l, IdGen idg)
    {
        this.store = s;
        this.log = l;
        this.idg = idg;
    }

    public PayResp pay(PayReq req)
    {
        String tx = idg.next("CARD-");
        Transaction t = new Transaction(tx, "CARD", req.getPayer(), req.getAmt(), Transaction.Status.PENDING);
        store.save(t);
        boolean ok = simulateNetwork(req);
        if (ok)
        {
            t.setStatus(Transaction.Status.SUCCESS);
            store.save(t);
            log.info("card pay ok " + tx);
            return new PayResp(tx, true, "card success");
        }
        else
        {
            t.setStatus(Transaction.Status.FAILED);
            store.save(t);
            log.err("card fail " + tx);
            return new PayResp(tx, false, "card failed");
        }
    }

    public PayResp refund(String txId, Money amt)
    {
        Optional<Transaction> o = store.find(txId);
        if (!o.isPresent()) return new PayResp("", false, "tx not found");
        Transaction t = o.get();
        if (!t.getMethod().equals("CARD")) return new PayResp(txId, false, "method mismatch");
        boolean ok = simulateRefund(amt);
        if (ok)
        {
            Transaction r = new Transaction(idg.next("R-"), "CARD-REFUND", t.getPayer(), amt, Transaction.Status.REFUNDED);
            store.save(r);
            t.setStatus(Transaction.Status.REFUNDED);
            store.save(t);
            log.info("card refund ok " + txId);
            return new PayResp(r.getId(), true, "refund success");
        }
        else
        {
            log.err("card refund fail " + txId);
            return new PayResp("", false, "refund failed");
        }
    }

    private boolean simulateNetwork(PayReq r)
    {
        return (r.getAmt().getAmount() % 2) == 0;
    }

    private boolean simulateRefund(Money amt)
    {
        return amt.getAmount() > 0;
    }
}

class UpiPayment implements IPayment, IRefund
{
    private final ITransactionStore s;
    private final ILogger l;
    private final IdGen g;

    public UpiPayment(ITransactionStore s, ILogger l, IdGen g)
    {
        this.s = s;
        this.l = l;
        this.g = g;
    }

    public PayResp pay(PayReq r)
    {
        String tx = g.next("UPI-");
        Transaction t = new Transaction(tx, "UPI", r.getPayer(), r.getAmt(), Transaction.Status.PENDING);
        s.save(t);
        boolean ok = sendToBank(r);
        if (ok)
        {
            t.setStatus(Transaction.Status.SUCCESS);
            s.save(t);
            l.info("upi ok " + tx);
            return new PayResp(tx, true, "upi success");
        }
        else
        {
            t.setStatus(Transaction.Status.FAILED);
            s.save(t);
            l.err("upi fail " + tx);
            return new PayResp(tx, false, "upi failed");
        }
    }

    public PayResp refund(String txId, Money amt)
    {
        Optional<Transaction> o = s.find(txId);
        if (!o.isPresent()) return new PayResp("", false, "tx not found");
        Transaction t = o.get();
        if (!t.getMethod().equals("UPI")) return new PayResp(txId, false, "method mismatch");
        boolean ok = reverse(amt);
        if (ok)
        {
            Transaction r = new Transaction(g.next("R-"), "UPI-REFUND", t.getPayer(), amt, Transaction.Status.REFUNDED);
            s.save(r);
            t.setStatus(Transaction.Status.REFUNDED);
            s.save(t);
            l.info("upi refund ok " + txId);
            return new PayResp(r.getId(), true, "refund success");
        }
        else
        {
            l.err("upi refund fail " + txId);
            return new PayResp("", false, "refund failed");
        }
    }

    private boolean sendToBank(PayReq r)
    {
        return r.getAmt().getAmount() % 5 != 0;
    }

    private boolean reverse(Money amt)
    {
        return amt.getAmount() <= 1000000;
    }
}

class WalletPayment implements IPayment
{
    private final ITransactionStore s;
    private final ILogger l;
    private final IdGen g;
    private final Map<String, Long> balances = new HashMap<>();

    public WalletPayment(ITransactionStore s, ILogger l, IdGen g)
    {
        this.s = s;
        this.l = l;
        this.g = g;
    }

    public void credit(String uid, long amount)
    {
        balances.put(uid, balances.getOrDefault(uid, 0L) + amount);
    }

    public PayResp pay(PayReq r)
    {
        String uid = r.getPayer().getId();
        long bal = balances.getOrDefault(uid, 0L);
        if (bal < r.getAmt().getAmount())
        {
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

// --- Factory & Service ---

class PayFactory
{
    private final Map<String, IPayment> map = new HashMap<>();
    private final Map<String, IRefund> refunds = new HashMap<>();

    public void register(String key, IPayment p)
    {
        map.put(key, p);
        if (p instanceof IRefund) refunds.put(key, (IRefund)p);
    }

    public IPayment get(String key)
    {
        return map.get(key);
    }

    public Optional<IRefund> getRefund(String key)
    {
        return Optional.ofNullable(refunds.get(key));
    }

    public Set<String> methods()
    {
        return new HashSet<>(map.keySet());
    }
}

class PaymentService
{
    private final PayFactory f;
    private final IValidator<PayReq> v;
    private final ILogger l;
    private final ITransactionStore s;

    public PaymentService(PayFactory f, IValidator<PayReq> v, ILogger l, ITransactionStore s)
    {
        this.f = f;
        this.v = v;
        this.l = l;
        this.s = s;
    }

    public PayResp execute(String method, PayReq req)
    {
        v.validate(req);
        IPayment p = f.get(method);
        if (p == null) return new PayResp("", false, "method unsupported");
        l.info("exec method " + method + " payer " + req.getPayer().getName());
        return p.pay(req);
    }

    public PayResp refund(String method, String txId, Money amt)
    {
        Optional<IRefund> ro = f.getRefund(method);
        if (!ro.isPresent()) return new PayResp("", false, "refund not supported");
        IRefund r = ro.get();
        l.info("refund method " + method + " tx " + txId);
        return r.refund(txId, amt);
    }

    public Optional<Transaction> find(String txId)
    {
        return s.find(txId);
    }
}

// --- Notifications (Extra) ---

interface INotifier
{
    void notify(String to, String msg);
}

class EmailNotifier implements INotifier
{
    public void notify(String to, String msg)
    {
        System.out.println("[EMAIL to " + to + "] " + msg);
    }
}

class SmsNotifier implements INotifier
{
    public void notify(String to, String msg)
    {
        System.out.println("[SMS to " + to + "] " + msg);
    }
}
