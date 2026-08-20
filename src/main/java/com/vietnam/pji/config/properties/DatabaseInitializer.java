package com.vietnam.pji.config.properties;

import java.util.ArrayList;
import java.util.List;

import com.vietnam.pji.model.auth.Permission;
import com.vietnam.pji.model.auth.Role;
import com.vietnam.pji.model.auth.User;
import com.vietnam.pji.repository.PermissionRepository;
import com.vietnam.pji.repository.RoleRepository;
import com.vietnam.pji.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class DatabaseInitializer implements CommandLineRunner {
        private final RoleRepository roleRepository;
        private final PermissionRepository permissionRepository;
        private final UserRepository userRepository;
        private final PasswordEncoder passwordEncoder;

        @Value("${app.bootstrap-admin.enabled:true}")
        private boolean bootstrapAdminEnabled;

        @Value("${app.bootstrap-admin.email:admin@example.com}")
        private String bootstrapAdminEmail;

        @Value("${app.bootstrap-admin.password:}")
        private String bootstrapAdminPassword;

        @Value("${app.bootstrap-admin.full-name:System Administrator}")
        private String bootstrapAdminFullName;

        public DatabaseInitializer(RoleRepository roleRepository, PermissionRepository permissionRepository,
                        UserRepository userRepository, PasswordEncoder passwordEncoder) {
                this.roleRepository = roleRepository;
                this.userRepository = userRepository;
                this.permissionRepository = permissionRepository;
                this.passwordEncoder = passwordEncoder;
        }

        @Override
        public void run(String... args) {
                System.out.println(">>>>>>>>> INITIAL DATABASE BEGINS:");
                long countPermission = this.permissionRepository.count();
                long countRole = this.roleRepository.count();
                long countUser = this.userRepository.count();

                if (countPermission == 0) {
                        ArrayList<Permission> arrResult = new ArrayList<>();
                        arrResult.add(new Permission("Get recommendation",
                                        "/api/v1/episodes/{episodeId}/ai-recommendations/generate", "POST", "RAG"));

                        arrResult.add(new Permission("Create a patient", "/api/v1/patients", "POST", "PATIENTS"));
                        arrResult.add(new Permission("Update a patient", "/api/v1/patients/{id}", "PUT", "PATIENTS"));
                        arrResult.add(new Permission("Delete a patient", "/api/v1/patients/{id}", "DELETE",
                                        "PATIENTS"));
                        arrResult.add(new Permission("Get patient by id", "/api/v1/patients/{id}", "GET", "PATIENTS"));
                        arrResult.add(new Permission("Get patients with pagination", "/api/v1/patients", "GET",
                                        "PATIENTS"));

                        arrResult.add(new Permission("Create an episode", "/api/v1/episodes", "POST", "EPISODES"));
                        arrResult.add(new Permission("Update an episode", "/api/v1/episodes/{id}", "PUT", "EPISODES"));
                        arrResult.add(new Permission("Delete an episode", "/api/v1/episodes/{id}", "DELETE",
                                        "EPISODES"));
                        arrResult.add(new Permission("Get episode by id", "/api/v1/episodes/{id}", "GET", "EPISODES"));
                        arrResult.add(new Permission("Get episodes with pagination", "/api/v1/episodes", "GET",
                                        "EPISODES"));
                        arrResult.add(new Permission("Get episodes by patient", "/api/v1/patients/{patientId}/episodes",
                                        "GET", "EPISODES"));

                        arrResult.add(new Permission("Create medical history",
                                        "/api/v1/episodes/{episodeId}/medical-history",
                                        "POST", "MEDICAL_HISTORY"));
                        arrResult.add(new Permission("Update medical history",
                                        "/api/v1/episodes/{episodeId}/medical-history",
                                        "PUT", "MEDICAL_HISTORY"));
                        arrResult.add(new Permission("Get medical history",
                                        "/api/v1/episodes/{episodeId}/medical-history", "GET",
                                        "MEDICAL_HISTORY"));

                        arrResult.add(new Permission("Create clinical record", "/api/v1/clinical-records", "POST",
                                        "CLINICAL_RECORDS"));
                        arrResult.add(new Permission("Update clinical record", "/api/v1/clinical-records/{id}", "PUT",
                                        "CLINICAL_RECORDS"));
                        arrResult.add(new Permission("Delete clinical record", "/api/v1/clinical-records/{id}",
                                        "DELETE", "CLINICAL_RECORDS"));
                        arrResult.add(new Permission("Get clinical record by id", "/api/v1/clinical-records/{id}",
                                        "GET", "CLINICAL_RECORDS"));
                        arrResult.add(new Permission("Get clinical records by episode",
                                        "/api/v1/episodes/{episodeId}/clinical-records", "GET", "CLINICAL_RECORDS"));

                        arrResult.add(new Permission("Create surgery", "/api/v1/surgeries", "POST", "SURGERIES"));
                        arrResult.add(new Permission("Update surgery", "/api/v1/surgeries/{id}", "PUT", "SURGERIES"));
                        arrResult.add(new Permission("Delete surgery", "/api/v1/surgeries/{id}", "DELETE",
                                        "SURGERIES"));
                        arrResult.add(new Permission("Get surgery by id", "/api/v1/surgeries/{id}", "GET",
                                        "SURGERIES"));
                        arrResult.add(new Permission("Get surgeries by episode",
                                        "/api/v1/episodes/{episodeId}/surgeries", "GET", "SURGERIES"));

                        arrResult.add(new Permission("Create lab result", "/api/v1/lab-results", "POST",
                                        "LAB_RESULTS"));
                        arrResult.add(new Permission("Update lab result", "/api/v1/lab-results/{id}", "PUT",
                                        "LAB_RESULTS"));
                        arrResult.add(new Permission("Delete lab result", "/api/v1/lab-results/{id}", "DELETE",
                                        "LAB_RESULTS"));
                        arrResult.add(new Permission("Get lab result by id", "/api/v1/lab-results/{id}", "GET",
                                        "LAB_RESULTS"));
                        arrResult.add(new Permission("Get lab results by episode",
                                        "/api/v1/episodes/{episodeId}/lab-results",
                                        "GET", "LAB_RESULTS"));

                        arrResult.add(new Permission("Create image result", "/api/v1/image-results", "POST",
                                        "IMAGE_RESULTS"));
                        arrResult.add(new Permission("Update image result", "/api/v1/image-results/{id}", "PUT",
                                        "IMAGE_RESULTS"));
                        arrResult.add(new Permission("Delete image result", "/api/v1/image-results/{id}", "DELETE",
                                        "IMAGE_RESULTS"));
                        arrResult.add(new Permission("Get image result by id", "/api/v1/image-results/{id}", "GET",
                                        "IMAGE_RESULTS"));
                        arrResult.add(new Permission("Get image results by episode",
                                        "/api/v1/episodes/{episodeId}/image-results",
                                        "GET", "IMAGE_RESULTS"));

                        arrResult.add(new Permission("Create culture result", "/api/v1/culture-results", "POST",
                                        "CULTURE_RESULTS"));
                        arrResult.add(new Permission("Update culture result", "/api/v1/culture-results/{id}", "PUT",
                                        "CULTURE_RESULTS"));
                        arrResult.add(new Permission("Delete culture result", "/api/v1/culture-results/{id}", "DELETE",
                                        "CULTURE_RESULTS"));
                        arrResult.add(new Permission("Get culture result by id", "/api/v1/culture-results/{id}", "GET",
                                        "CULTURE_RESULTS"));
                        arrResult.add(new Permission("Get culture results by episode",
                                        "/api/v1/episodes/{episodeId}/culture-results", "GET", "CULTURE_RESULTS"));

                        arrResult.add(new Permission("Create sensitivity result", "/api/v1/sensitivity-results", "POST",
                                        "SENSITIVITY_RESULTS"));
                        arrResult.add(new Permission("Update sensitivity result", "/api/v1/sensitivity-results/{id}",
                                        "PUT", "SENSITIVITY_RESULTS"));
                        arrResult.add(new Permission("Delete sensitivity result", "/api/v1/sensitivity-results/{id}",
                                        "DELETE", "SENSITIVITY_RESULTS"));
                        arrResult.add(new Permission("Get sensitivity result by id", "/api/v1/sensitivity-results/{id}",
                                        "GET", "SENSITIVITY_RESULTS"));
                        arrResult.add(new Permission("Get sensitivity results by culture",
                                        "/api/v1/culture-results/{cultureId}/sensitivity-results", "GET",
                                        "SENSITIVITY_RESULTS"));

                        arrResult.add(new Permission("Create a permission", "/api/v1/permissions", "POST",
                                        "PERMISSIONS"));
                        arrResult.add(new Permission("Update a permission", "/api/v1/permissions", "PUT",
                                        "PERMISSIONS"));
                        arrResult.add(new Permission("Delete a permission", "/api/v1/permissions/{id}",
                                        "DELETE", "PERMISSIONS"));
                        arrResult.add(new Permission("Get a permission by id", "/api/v1/permissions/{id}", "GET",
                                        "PERMISSIONS"));
                        arrResult.add(new Permission("Get permission with pagination", "/api/v1/permissions",
                                        "GET", "PERMISSIONS"));

                        arrResult.add(new Permission("Create a role", "/api/v1/roles", "POST", "ROLES"));
                        arrResult.add(new Permission("Update a role", "/api/v1/roles", "PUT", "ROLES"));
                        arrResult.add(new Permission("Delete a role", "/api/v1/roles/{id}", "DELETE", "ROLES"));
                        arrResult.add(new Permission("Get role by id", "/api/v1/role/{id}", "GET", "ROLES"));
                        arrResult.add(new Permission("Get roles with pagination", "/api/v1/roles", "GET", "ROLES"));

                        arrResult.add(new Permission("Create a user", "/api/v1/users", "POST", "USERS"));
                        arrResult.add(new Permission("Update a user", "/api/v1/users", "PUT", "USERS"));
                        arrResult.add(new Permission("Delete a user", "/api/v1/users/{id}", "DELETE", "USERS"));
                        arrResult.add(new Permission("Get a user by id", "/api/v1/user/{id}", "GET", "USERS"));
                        arrResult.add(new Permission("Get users with pagination", "/api/v1/users", "GET", "USERS"));

                        arrResult.add(new Permission("Upload file", "/api/v1/files", "POST", "FILES"));

                        arrResult.add(new Permission("Generate recommendation (sync)",
                                        "/api/v1/episodes/{episodeId}/ai-recommendations/generate-sync", "POST",
                                        "RAG"));
                        arrResult.add(new Permission("Get recommendation runs by episode",
                                        "/api/v1/episodes/{episodeId}/ai-recommendations/runs", "GET", "RAG"));
                        arrResult.add(new Permission("Get recommendation run detail",
                                        "/api/v1/ai-recommendations/runs/{runId}", "GET", "RAG"));
                        arrResult.add(new Permission("Retry recommendation run",
                                        "/api/v1/ai-recommendations/runs/{runId}/retry", "POST", "RAG"));
                        arrResult.add(new Permission("Stream recommendation thought logs",
                                        "/api/v1/ai-recommendations/runs/{runId}/stream", "GET", "RAG"));

                        arrResult.add(new Permission("Create AI chat session",
                                        "/api/v1/ai-chat/sessions", "POST", "AI_CHAT"));
                        arrResult.add(new Permission("Send AI chat message",
                                        "/api/v1/ai-chat/sessions/{sessionId}/messages", "POST", "AI_CHAT"));
                        arrResult.add(new Permission("Get AI chat messages",
                                        "/api/v1/ai-chat/sessions/{sessionId}/messages", "GET", "AI_CHAT"));
                        arrResult.add(new Permission("Get AI chat sessions by episode",
                                        "/api/v1/episodes/{episodeId}/ai-chat/sessions", "GET", "AI_CHAT"));

                        arrResult.add(new Permission("Create doctor review",
                                        "/api/v1/episodes/{episodeId}/doctor-reviews", "POST", "DOCTOR_REVIEWS"));
                        arrResult.add(new Permission("Get doctor review by run",
                                        "/api/v1/ai-recommendations/runs/{runId}/review", "GET", "DOCTOR_REVIEWS"));
                        arrResult.add(new Permission("Get doctor reviews by episode",
                                        "/api/v1/episodes/{episodeId}/doctor-reviews", "GET", "DOCTOR_REVIEWS"));
                        arrResult.add(new Permission("Get final decision by episode",
                                        "/api/v1/episodes/{episodeId}/doctor-reviews/final-decision", "GET",
                                        "DOCTOR_REVIEWS"));
                        arrResult.add(new Permission("Select final decision version",
                                        "/api/v1/episodes/{episodeId}/doctor-reviews/{reviewId}/final-decision", "PUT",
                                        "DOCTOR_REVIEWS"));
                        arrResult.add(new Permission("Get my pending lab tasks",
                                        "/api/v1/pending-lab-tasks/my", "GET", "PENDING_LAB_TASKS"));
                        arrResult.add(new Permission("Get my pending lab task count",
                                        "/api/v1/pending-lab-tasks/my/count", "GET", "PENDING_LAB_TASKS"));
                        arrResult.add(new Permission("Dismiss pending lab task",
                                        "/api/v1/pending-lab-tasks/{id}/dismiss", "POST", "PENDING_LAB_TASKS"));
                        arrResult.add(new Permission("Quick-entry pending lab task",
                                        "/api/v1/pending-lab-tasks/{id}/quick-entry", "POST", "PENDING_LAB_TASKS"));
                        arrResult.add(new Permission("Create pending lab tasks from completeness",
                                        "/api/v1/episodes/{episodeId}/pending-lab-tasks/from-completeness", "POST",
                                        "PENDING_LAB_TASKS"));

                        arrResult.add(new Permission("Create extract-images job",
                                        "/api/v1/extract-images/jobs", "POST", "EXTRACT_IMAGES"));
                        arrResult.add(new Permission("Get extract-images job",
                                        "/api/v1/extract-images/jobs/{jobId}", "GET", "EXTRACT_IMAGES"));

                        this.permissionRepository.saveAll(arrResult);
                }

                if (countRole == 0) {
                        List<Permission> permissions = this.permissionRepository.findAll();

                        Role initRole = new Role();
                        initRole.setName("ADMIN");
                        initRole.setDescription("Contain full of permissions on this web service");
                        initRole.setActive(true);
                        initRole.setPermissions(permissions);

                        this.roleRepository.save(initRole);
                }

                if (countUser == 0 && bootstrapAdminEnabled) {
                        if (bootstrapAdminPassword == null || bootstrapAdminPassword.isBlank()) {
                                throw new IllegalStateException(
                                                "app.bootstrap-admin.password must be configured when bootstrap admin is enabled");
                        }

                        User initUser = new User();
                        initUser.setFullName(bootstrapAdminFullName);
                        initUser.setEmail(bootstrapAdminEmail);
                        initUser.setPassword(this.passwordEncoder.encode(bootstrapAdminPassword));

                        Role userRole = this.roleRepository.findByName("ADMIN");
                        if (userRole != null) {
                                initUser.setRole(userRole);
                        }
                        this.userRepository.save(initUser);
                }

                if (countRole > 0 && countPermission > 0 && countUser > 0) {
                        System.out.println("SKIP INITIAL DATABASE");
                } else {
                        System.out.println("END TASK");
                }
        }
}
