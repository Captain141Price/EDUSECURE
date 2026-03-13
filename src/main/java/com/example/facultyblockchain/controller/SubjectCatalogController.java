package com.example.facultyblockchain.controller;

import com.example.facultyblockchain.model.SubjectEntry;
import com.example.facultyblockchain.service.SubjectCatalogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * SubjectCatalogController
 *
 * Exposes the subject catalog loaded from subjects.xlsx to the Admin UI.
 *
 * Endpoints
 * ---------
 * GET  /subjects/filter?department=CSE1&semester=5
 *      Returns a list of SubjectEntry objects for the given dept + semester.
 *      The department is normalised server-side (CSE1/2/3 → CSE).
 *
 * POST /subjects/reload
 *      Forces a cache reload from disk (use after updating subjects.xlsx
 *      without restarting the server). Restricted to admins in production.
 *
 * GET  /subjects/health
 *      Returns the number of subjects currently cached — sanity check.
 */
@RestController
@RequestMapping("/subjects")
@CrossOrigin("*")
public class SubjectCatalogController {

    @Autowired
    private SubjectCatalogService subjectCatalogService;

    /**
     * Primary endpoint: filter subjects by department and semester.
     *
     * Query params:
     *   department  — raw value from UI dropdown (e.g. "CSE1", "AIML", "ECE")
     *   semester    — numeric string (e.g. "5")
     *
     * Each result object in the JSON array:
     * {
     *   "department":  "CSE",
     *   "semester":     5,
     *   "courseName":  "Artificial Intelligence",
     *   "courseCode":  "PEC-IT501B",
     *   "subjectType": "THEORY",
     *   "elective":    true        ← computed flag added in response
     * }
     *
     * The "elective" flag signals to the UI whether manual student
     * selection is required (sem ≥ 5 AND PEC/OEC prefix).
     */
    @GetMapping("/filter")
    public ResponseEntity<List<SubjectEntryWithElectiveFlag>> filterSubjects(
            @RequestParam String department,
            @RequestParam String semester) {

        List<SubjectEntry> entries =
                subjectCatalogService.getSubjects(department, semester);

        List<SubjectEntryWithElectiveFlag> result = entries.stream()
                .map(e -> new SubjectEntryWithElectiveFlag(e,
                          SubjectCatalogService.isElective(e)))
                .toList();

        return ResponseEntity.ok(result);
    }

    /**
     * Reload the in-memory cache from subjects.xlsx without server restart.
     * Returns the new cache size so the caller can verify the reload.
     */
    @PostMapping("/reload")
    public ResponseEntity<Map<String, Object>> reloadCache() {
        subjectCatalogService.reloadCache();
        return ResponseEntity.ok(Map.of(
                "status",  "success",
                "message", "Subject catalog reloaded from subjects.xlsx",
                "count",   subjectCatalogService.getCacheSize()
        ));
    }

    /**
     * Health / diagnostic endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status",          "ok",
                "cachedSubjects",  subjectCatalogService.getCacheSize(),
                "departments",     subjectCatalogService.getDepartments()
        ));
    }

    // -----------------------------------------------------------------------
    // Inner DTO: SubjectEntry + computed isElective flag
    // -----------------------------------------------------------------------

    /**
     * Response DTO that wraps SubjectEntry and adds the computed "elective"
     * boolean. Keeps the model class clean.
     */
    public static class SubjectEntryWithElectiveFlag {
        private final String  department;
        private final int     semester;
        private final String  courseName;
        private final String  courseCode;
        private final String  subjectType;
        private final boolean elective;

        SubjectEntryWithElectiveFlag(SubjectEntry entry, boolean elective) {
            this.department  = entry.getDepartment();
            this.semester    = entry.getSemester();
            this.courseName  = entry.getCourseName();
            this.courseCode  = entry.getCourseCode();
            this.subjectType = entry.getSubjectType();
            this.elective    = elective;
        }

        public String  getDepartment()  { return department; }
        public int     getSemester()    { return semester; }
        public String  getCourseName()  { return courseName; }
        public String  getCourseCode()  { return courseCode; }
        public String  getSubjectType() { return subjectType; }
        public boolean isElective()     { return elective; }
    }
}
