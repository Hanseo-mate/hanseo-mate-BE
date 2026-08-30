package hsu.hanseomate.domain.campusmap;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hsu.hanseomate.domain.campusmap.entity.CampusBuilding;
import hsu.hanseomate.domain.campusmap.entity.CampusBuildingAlias;
import hsu.hanseomate.domain.campusmap.repository.CampusBuildingAliasRepository;
import hsu.hanseomate.domain.campusmap.repository.CampusBuildingRepository;
import hsu.hanseomate.domain.campusmap.type.CampusCode;
import hsu.hanseomate.domain.course.entity.CourseOffering;
import hsu.hanseomate.domain.course.repository.CourseOfferingRepository;
import hsu.hanseomate.domain.courseimport.dto.CourseImportResponse;
import hsu.hanseomate.domain.courseimport.dto.TimetableParseResultRequest;
import hsu.hanseomate.domain.courseimport.dto.type.StorageStatus;
import hsu.hanseomate.domain.courseimport.service.CourseImportService;
import hsu.hanseomate.domain.timetable.composition.entity.Timetable;
import hsu.hanseomate.domain.timetable.composition.entity.TimetableCourse;
import hsu.hanseomate.domain.timetable.composition.repository.TimetableCourseRepository;
import hsu.hanseomate.domain.timetable.composition.repository.TimetableRepository;
import hsu.hanseomate.global.security.JwtProperties;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(CampusMapApiIntegrationTest.FixedClockConfiguration.class)
class CampusMapApiIntegrationTest {

    private static final String ENDPOINT = "/api/timetables/today-locations";
    private static final String WEEKLY_ENDPOINT =
            "/api/timetables/weekly-locations";
    private static final String FIXTURE =
            "fixtures/course-import/course-search-major-2026-1.json";
    private static final Long CURRENT_USER_ID = 301L;
    private static final Long OTHER_USER_ID = 302L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CourseImportService courseImportService;

    @Autowired
    private CourseOfferingRepository courseOfferingRepository;

    @Autowired
    private CampusBuildingRepository campusBuildingRepository;

    @Autowired
    private CampusBuildingAliasRepository campusBuildingAliasRepository;

    @Autowired
    private TimetableRepository timetableRepository;

    @Autowired
    private TimetableCourseRepository timetableCourseRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        seedCampusBuildings();
        jdbcTemplate.update(
                """
                        INSERT INTO user_accounts (
                            id,
                            login_id,
                            password_hash,
                            role,
                            preferred_restaurant_type,
                            created_at,
                            updated_at
                        ) VALUES
                            (?, ?, ?, 'USER', 'MAIN_STUDENT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                            (?, ?, ?, 'USER', 'MAIN_STUDENT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                CURRENT_USER_ID,
                "campus-map-current-user",
                "test-password-hash",
                OTHER_USER_ID,
                "campus-map-other-user",
                "test-password-hash"
        );
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    void returnsMappedUnmappedAndNoClassroomLocationsForTodayInStableOrder()
            throws Exception {
        String payload = fixturePayload()
                .replace("\"TUESDAY\"", "\"MONDAY\"")
                .replace("\"WEDNESDAY\"", "\"MONDAY\"")
                .replace("\"THURSDAY\"", "\"MONDAY\"");
        importMajorFixture(payload);
        jdbcTemplate.update(
                """
                        UPDATE course_schedules
                        SET classroom_id = NULL
                        WHERE course_id = (
                            SELECT id FROM courses WHERE course_code = '004000'
                        )
                        """
        );

        Timetable timetable = timetableRepository.saveAndFlush(
                Timetable.create(CURRENT_USER_ID, 2026, 1)
        );
        addCourse(timetable, "003000");
        addCourse(timetable, "005000");
        addCourse(timetable, "002000");
        addCourse(timetable, "004000");

        Timetable otherTimetable = timetableRepository.saveAndFlush(
                Timetable.create(OTHER_USER_ID, 2026, 1)
        );
        addCourse(otherTimetable, "001000");

        Timetable previousYearTimetable = timetableRepository.saveAndFlush(
                Timetable.create(CURRENT_USER_ID, 2025, 1)
        );
        addCourse(previousYearTimetable, "001000");

        Timetable otherSemesterTimetable = timetableRepository.saveAndFlush(
                Timetable.create(CURRENT_USER_ID, 2026, 2)
        );
        addCourse(otherSemesterTimetable, "001000");

        mockMvc.perform(get(ENDPOINT)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + validAccessToken(CURRENT_USER_ID.toString())
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-05-11"))
                .andExpect(jsonPath("$.dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.academicYear").value(2026))
                .andExpect(jsonPath("$.semester").value(1))
                .andExpect(jsonPath("$.courseLocations.length()").value(4))
                .andExpect(jsonPath("$.courseLocations[0].scheduleId")
                        .value(notNullValue()))
                .andExpect(jsonPath("$.courseLocations[0].courseName")
                        .value("알파개론"))
                .andExpect(jsonPath("$.courseLocations[0].periods[0]").value(0))
                .andExpect(jsonPath("$.courseLocations[0].periods[1]").value(1))
                .andExpect(jsonPath("$.courseLocations[0].campusCode")
                        .value("TAEAN"))
                .andExpect(jsonPath("$.courseLocations[0].buildingName")
                        .value("본관"))
                .andExpect(jsonPath("$.courseLocations[0].roomNumber")
                        .value("101"))
                .andExpect(jsonPath("$.courseLocations[0].canonicalBuildingName")
                        .value("태안 강의동(본관)"))
                .andExpect(jsonPath("$.courseLocations[0].latitude")
                        .value(36.5944988))
                .andExpect(jsonPath("$.courseLocations[0].longitude")
                        .value(126.294045))
                .andExpect(jsonPath("$.courseLocations[0].locationStatus")
                        .value("MAPPED"))
                .andExpect(jsonPath("$.courseLocations[1].courseName")
                        .value("기타세미나"))
                .andExpect(jsonPath("$.courseLocations[1].locationStatus")
                        .value("UNMAPPED"))
                .andExpect(jsonPath("$.courseLocations[1].latitude")
                        .value(nullValue()))
                .andExpect(jsonPath("$.courseLocations[1].longitude")
                        .value(nullValue()))
                .andExpect(jsonPath("$.courseLocations[2].courseName")
                        .value("베타연구"))
                .andExpect(jsonPath("$.courseLocations[2].canonicalBuildingName")
                        .value("태안 강의동(본관)"))
                .andExpect(jsonPath("$.courseLocations[2].latitude")
                        .value(36.5944988))
                .andExpect(jsonPath("$.courseLocations[2].longitude")
                        .value(126.294045))
                .andExpect(jsonPath("$.courseLocations[2].locationStatus")
                        .value("MAPPED"))
                .andExpect(jsonPath("$.courseLocations[3].courseName")
                        .value("델타프로젝트"))
                .andExpect(jsonPath("$.courseLocations[3].locationStatus")
                        .value("NO_CLASSROOM"))
                .andExpect(jsonPath("$.courseLocations[3].buildingName")
                        .value(nullValue()))
                .andExpect(jsonPath("$.courseLocations[3].latitude")
                        .value(nullValue()))
                .andExpect(jsonPath("$.courseLocations[*].courseName")
                        .value(not(hasItem("찰리실습"))));
    }

    @Test
    void returnsEmptyLocationsWhenCurrentTermTimetableDoesNotExist()
            throws Exception {
        String accessToken = validAccessToken(CURRENT_USER_ID.toString());
        mockMvc.perform(get(ENDPOINT)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + accessToken
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-05-11"))
                .andExpect(jsonPath("$.courseLocations").isEmpty());

        mockMvc.perform(get(WEEKLY_ENDPOINT)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + accessToken
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.academicYear").value(2026))
                .andExpect(jsonPath("$.semester").value(1))
                .andExpect(jsonPath("$.dayLocations.length()").value(4))
                .andExpect(jsonPath("$.dayLocations[0].courseLocations")
                        .isEmpty())
                .andExpect(jsonPath("$.dayLocations[1].courseLocations")
                        .isEmpty())
                .andExpect(jsonPath("$.dayLocations[2].courseLocations")
                        .isEmpty())
                .andExpect(jsonPath("$.dayLocations[3].courseLocations")
                        .isEmpty());
    }

    @Test
    void returnsMondayThroughThursdayLocationsGroupedByDayOfWeek()
            throws Exception {
        importMajorFixture(fixturePayload());
        jdbcTemplate.update(
                """
                        UPDATE course_schedules
                        SET classroom_id = NULL
                        WHERE course_id = (
                            SELECT id FROM courses WHERE course_code = '004000'
                        )
                        """
        );

        Timetable timetable = timetableRepository.saveAndFlush(
                Timetable.create(CURRENT_USER_ID, 2026, 1)
        );
        addCourse(timetable, "001000");
        addCourse(timetable, "002000");
        addCourse(timetable, "003000");
        addCourse(timetable, "004000");
        addCourse(timetable, "005000");

        mockMvc.perform(get(WEEKLY_ENDPOINT)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + validAccessToken(CURRENT_USER_ID.toString())
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.academicYear").value(2026))
                .andExpect(jsonPath("$.semester").value(1))
                .andExpect(jsonPath("$.dayLocations.length()").value(4))
                .andExpect(jsonPath("$.dayLocations[0].dayOfWeek")
                        .value("MONDAY"))
                .andExpect(jsonPath(
                        "$.dayLocations[0].courseLocations.length()"
                ).value(2))
                .andExpect(jsonPath(
                        "$.dayLocations[0].courseLocations[0].courseName"
                ).value("알파개론"))
                .andExpect(jsonPath(
                        "$.dayLocations[0].courseLocations[0].locationStatus"
                ).value("MAPPED"))
                .andExpect(jsonPath(
                        "$.dayLocations[0].courseLocations[1].courseName"
                ).value("기타세미나"))
                .andExpect(jsonPath("$.dayLocations[1].dayOfWeek")
                        .value("TUESDAY"))
                .andExpect(jsonPath(
                        "$.dayLocations[1].courseLocations[0].courseName"
                ).value("찰리실습"))
                .andExpect(jsonPath("$.dayLocations[2].dayOfWeek")
                        .value("WEDNESDAY"))
                .andExpect(jsonPath(
                        "$.dayLocations[2].courseLocations[0].courseName"
                ).value("베타연구"))
                .andExpect(jsonPath(
                        "$.dayLocations[2].courseLocations[0].locationStatus"
                ).value("MAPPED"))
                .andExpect(jsonPath("$.dayLocations[3].dayOfWeek")
                        .value("THURSDAY"))
                .andExpect(jsonPath(
                        "$.dayLocations[3].courseLocations[0].locationStatus"
                ).value("NO_CLASSROOM"))
                .andExpect(jsonPath("$.dayLocations[*].dayOfWeek")
                        .value(not(hasItem("FRIDAY"))));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value(ENDPOINT));

        mockMvc.perform(get(WEEKLY_ENDPOINT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value(WEEKLY_ENDPOINT));
    }

    @Test
    void exposesCampusMapEndpointInOpenApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/timetables/today-locations'].get"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.CampusMapTodayResponse"
                                + ".properties.courseLocations.items['$ref']"
                ).value("#/components/schemas/CampusMapCourseLocationResponse"))
                .andExpect(jsonPath(
                        "$.paths['/api/timetables/weekly-locations'].get"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.CampusMapWeeklyResponse"
                                + ".properties.dayLocations.items['$ref']"
                ).value("#/components/schemas/CampusMapDayLocationsResponse"))
                .andExpect(jsonPath(
                        "$.components.schemas.CampusMapDayLocationsResponse"
                                + ".properties.courseLocations.items['$ref']"
                ).value("#/components/schemas/CampusMapCourseLocationResponse"));
    }

    private void addCourse(Timetable timetable, String courseCode) {
        timetableCourseRepository.saveAndFlush(TimetableCourse.create(
                timetable,
                offeringByCode(courseCode)
        ));
    }

    private CourseOffering offeringByCode(String courseCode) {
        return courseOfferingRepository.findAll().stream()
                .filter(offering -> courseCode.equals(offering.getCourseCode()))
                .findFirst()
                .orElseThrow();
    }

    private void importMajorFixture(String payload) {
        CourseImportResponse response = courseImportService.importCourses(
                objectMapper.readValue(payload, TimetableParseResultRequest.class)
        );
        org.assertj.core.api.Assertions.assertThat(response.storageStatus())
                .isEqualTo(StorageStatus.STORED);
    }

    private String fixturePayload() throws Exception {
        return new ClassPathResource(FIXTURE)
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private String validAccessToken(String subject) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .subject(subject)
                .claim("role", "USER")
                .issuedAt(now.minusSeconds(60))
                .expiresAt(now.plusSeconds(3_600))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256)
                .type("JWT")
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();
    }

    private void seedCampusBuildings() {
        CampusBuilding seosanMain = campusBuildingRepository.saveAndFlush(
                CampusBuilding.create(
                        CampusCode.SEOSAN,
                        "자악관",
                        new BigDecimal("36.6914647"),
                        new BigDecimal("126.5889642")
                )
        );
        CampusBuilding taeAnMain = campusBuildingRepository.saveAndFlush(
                CampusBuilding.create(
                        CampusCode.TAEAN,
                        "태안 강의동(본관)",
                        new BigDecimal("36.5944988"),
                        new BigDecimal("126.294045")
                )
        );
        campusBuildingAliasRepository.saveAllAndFlush(List.of(
                CampusBuildingAlias.create(seosanMain, "본관"),
                CampusBuildingAlias.create(taeAnMain, "본관"),
                CampusBuildingAlias.create(taeAnMain, "비행교육원")
        ));
    }

    private void cleanDatabase() {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        try {
            truncate("campus_building_aliases");
            truncate("campus_buildings");
            truncate("timetable_courses");
            truncate("timetables");
            truncate("user_accounts");
            truncate("course_import_issues");
            truncate("course_schedules");
            truncate("course_source_cells");
            truncate("offering_allowed_grades");
            truncate("offering_eligible_departments");
            truncate("offering_general_education");
            truncate("course_offerings");
            truncate("semester_academic_units");
            truncate("semester_general_category_nodes");
            truncate("course_import_histories");
            truncate("classrooms");
            truncate("courses");
            truncate("academic_units");
            truncate("semesters");
        } finally {
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }

    private void truncate(String table) {
        jdbcTemplate.execute("TRUNCATE TABLE " + table);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(
                    Instant.parse("2026-05-10T15:30:00Z"),
                    ZoneOffset.UTC
            );
        }
    }
}
