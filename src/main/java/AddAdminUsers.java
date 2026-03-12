import com.example.facultyblockchain.service.LoginBlockchainService;

public class AddAdminUsers {
    public static void main(String[] args) {
        LoginBlockchainService service = new LoginBlockchainService();

        // Add Super Admin
        service.addUser("SuperAdmin", "superadmin@college.edu", "super123", "superadmin");

        // Add Admin
        service.addUser("AdminUser", "cs23.sahasin.mondal@stcet.ac.in", "admin123", "admin");

        System.out.println("SuperAdmin.dat and Admin.dat have been updated with new users!");
    }
}
