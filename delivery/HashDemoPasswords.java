import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Generates hashes only for the documented public demo credentials. */
public class HashDemoPasswords {
    public static void main(String[] args) {
        var encoder = new BCryptPasswordEncoder();
        System.out.println(encoder.encode("Admin@123"));
        System.out.println(encoder.encode("Student@123"));
    }
}
