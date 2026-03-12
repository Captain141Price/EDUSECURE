import java.io.*;
import java.util.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.facultyblockchain.model.Block;

public class TestMarStatus {
    public static void main(String[] args) throws Exception {
        String path = "MarBlockchain.dat"; 
        File file = new File(path);
        if (!file.exists()) {
            System.out.println("No MarBlockchain.dat found at " + file.getAbsolutePath());
            return;
        }

        ObjectInputStream in = new ObjectInputStream(new FileInputStream(file));
        List<Block> blocks = (List<Block>) in.readObject();
        in.close();

        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, String> reviewStatuses = new HashMap<>();

        for (Block b : blocks) {
            if (b.getProjects() != null && !b.getProjects().isEmpty()) {
                Map<String, Object> data = objectMapper.readValue(b.getProjects(), new TypeReference<Map<String, Object>>() {});
                if ("MENTOR_REVIEW".equals(data.get("action"))) {
                    reviewStatuses.put((String) data.get("originalHash"), (String) data.get("status"));
                }
            }
        }

        System.out.println("Review Statuses Extracted: " + reviewStatuses);

        for (Block b : blocks) {
            if (b.getProjects() != null && !b.getProjects().isEmpty()) {
                Map<String, Object> data = objectMapper.readValue(b.getProjects(), new TypeReference<Map<String, Object>>() {});
                if ("MENTOR_REVIEW".equals(data.get("action"))) continue;
                System.out.println("Student block hash: " + b.getHash() + " -> Status: " + reviewStatuses.getOrDefault(b.getHash(), "Pending"));
            }
        }
    }
}
