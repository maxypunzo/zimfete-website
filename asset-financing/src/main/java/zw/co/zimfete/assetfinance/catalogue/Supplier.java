package zw.co.zimfete.assetfinance.catalogue;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "supplier")
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String phone;
    private String email;
    private String address;
    private boolean active;
    private Instant createdAt;

    protected Supplier() {
    }

    public Supplier(String name, String phone, String email, String address) {
        update(name, phone, email, address, true);
    }

    public void update(String name, String phone, String email, String address, boolean active) {
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.address = address;
        this.active = active;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getAddress() { return address; }
    public boolean isActive() { return active; }
}
