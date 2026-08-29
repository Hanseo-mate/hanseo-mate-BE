package hsu.hanseomate.domain.campusmap;

import static hsu.hanseomate.support.AdminJwtRequestPostProcessor.adminJwt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Comparator;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CampusPlaceApiIntegrationTest {

    private static final String PLACES_ENDPOINT = "/api/campus-map/places";
    private static final String IMAGE_ENDPOINT =
            "/api/admin/campus-map/place-images";
    private static final String ADMIN_PLACES_ENDPOINT =
            "/api/admin/campus-map/places";
    private static final String ALLOWED_ORIGIN = "http://localhost:3000";
    private static final Path TEST_IMAGE_DIRECTORY = Path.of(
            "build",
            "test-uploads",
            "campus-places"
    ).toAbsolutePath().normalize();
    private static final byte[] TINY_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUB"
                    + "AScY42YAAAAASUVORK5CYII="
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() throws Exception {
        cleanCampusPlaceTables();
        deleteUploadedImages();
        insertPlace(
                1L,
                "SEOSAN",
                "가배앤빈",
                "가배앤빈",
                "CAFE",
                "한서대학교 대정문 인근 카페",
                "https://old.example/uploads/campus-places/cafe.png",
                "36.691166000",
                "126.574659000"
        );
        insertPlace(
                2L,
                "TAEAN",
                "태안본관",
                "태안본관",
                "LECTURE_BUILDING",
                "태안캠퍼스의 주요 강의 건물",
                null,
                "36.594581000",
                "126.294056000"
        );
        insertPlace(
                3L,
                "SEOSAN",
                "미분류 장소",
                "미분류장소",
                null,
                null,
                null,
                "36.690000000",
                "126.580000000"
        );
        jdbcTemplate.update(
                """
                        INSERT INTO campus_lecture_building_details (
                            place_id,
                            location_description,
                            floor_count,
                            has_elevator,
                            operating_hours,
                            created_at,
                            updated_at
                        ) VALUES (2, '태안캠퍼스', 4, TRUE, '평일 09:00~22:00',
                                  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """
        );
        jdbcTemplate.update(
                """
                        INSERT INTO campus_lecture_building_departments (
                            place_id, sort_order, department_name
                        ) VALUES
                            (2, 0, '항공운항학과'),
                            (2, 1, '헬리콥터조종학과')
                        """
        );
        jdbcTemplate.update(
                """
                        INSERT INTO campus_lecture_building_facilities (
                            place_id, sort_order, facility_name
                        ) VALUES
                            (2, 0, '강의실'),
                            (2, 1, '학과사무실')
                        """
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        cleanCampusPlaceTables();
        deleteUploadedImages();
    }

    @Test
    void listsPublicPlacesAndFiltersByCampusAndCategory() throws Exception {
        mockMvc.perform(get(PLACES_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places.length()").value(3));

        mockMvc.perform(get(PLACES_ENDPOINT)
                        .queryParam("campusCode", "SEOSAN")
                        .queryParam("category", "CAFE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places.length()").value(1))
                .andExpect(jsonPath("$.places[0].placeId").value(1))
                .andExpect(jsonPath("$.places[0].campusCode").value("SEOSAN"))
                .andExpect(jsonPath("$.places[0].placeName").value("가배앤빈"))
                .andExpect(jsonPath("$.places[0].category").value("CAFE"))
                .andExpect(jsonPath("$.places[0].categoryName").value("카페"))
                .andExpect(jsonPath("$.places[0].oneLineDescription")
                        .value("한서대학교 대정문 인근 카페"))
                .andExpect(jsonPath("$.places[0].imageUrl")
                        .value("http://localhost/uploads/campus-places/cafe.png"))
                .andExpect(jsonPath("$.places[0].latitude").value(36.691166))
                .andExpect(jsonPath("$.places[0].longitude").value(126.574659));
    }

    @Test
    void returnsNullMetadataForPlacesThatUserHasNotClassifiedYet()
            throws Exception {
        mockMvc.perform(get(PLACES_ENDPOINT + "/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeName").value("미분류 장소"))
                .andExpect(jsonPath("$.category").doesNotExist())
                .andExpect(jsonPath("$.categoryName").doesNotExist())
                .andExpect(jsonPath("$.oneLineDescription").doesNotExist())
                .andExpect(jsonPath("$.imageUrl").doesNotExist());
    }

    @Test
    void returnsNotFoundForUnknownPlace() throws Exception {
        mockMvc.perform(get(PLACES_ENDPOINT + "/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value(PLACES_ENDPOINT + "/999"));
    }

    @Test
    void returnsLectureBuildingSpecificDetails() throws Exception {
        mockMvc.perform(get(PLACES_ENDPOINT + "/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeName").value("태안본관"))
                .andExpect(jsonPath("$.category").value("LECTURE_BUILDING"))
                .andExpect(jsonPath("$.categoryName").value("강의실"))
                .andExpect(jsonPath("$.lectureBuildingDetails.location")
                        .value("태안캠퍼스"))
                .andExpect(jsonPath("$.lectureBuildingDetails.floorCount")
                        .value(4))
                .andExpect(jsonPath("$.lectureBuildingDetails.hasElevator")
                        .value(true))
                .andExpect(jsonPath("$.lectureBuildingDetails.operatingHours")
                        .value("평일 09:00~22:00"))
                .andExpect(jsonPath("$.lectureBuildingDetails.departments[0]")
                        .value("항공운항학과"))
                .andExpect(jsonPath("$.lectureBuildingDetails.departments[1]")
                        .value("헬리콥터조종학과"))
                .andExpect(jsonPath("$.lectureBuildingDetails.majorFacilities[0]")
                        .value("강의실"))
                .andExpect(jsonPath("$.lectureBuildingDetails.majorFacilities[1]")
                        .value("학과사무실"));
    }

    @Test
    void allowsConfiguredFrontendOriginForPublicPlaceReads() throws Exception {
        mockMvc.perform(options(PLACES_ENDPOINT)
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(
                                HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                HttpMethod.GET.name()
                        ))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        ALLOWED_ORIGIN
                ));
    }

    @Test
    void uploadsImageForAdminAndDoesNotUpdatePlaceRow() throws Exception {
        MvcResult result = mockMvc.perform(multipart(IMAGE_ENDPOINT)
                        .file(pngFile("place.png"))
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").isString())
                .andReturn();

        String imageUrl = JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.imageUrl"
        );
        assertThat(imageUrl)
                .startsWith("http://localhost/uploads/campus-places/");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT image_url FROM campus_places WHERE id = 2",
                String.class
        )).isNull();

        mockMvc.perform(get(URI.create(imageUrl).getPath()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(TINY_PNG));
    }

    @Test
    void protectsImageUploadWithAdminRole() throws Exception {
        mockMvc.perform(multipart(IMAGE_ENDPOINT).file(pngFile("anonymous.png")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(multipart(IMAGE_ENDPOINT)
                        .file(pngFile("user.png"))
                        .with(jwt().authorities(
                                new SimpleGrantedAuthority("ROLE_USER")
                        )))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsInvalidImageWithoutCreatingAFile() throws Exception {
        MockMultipartFile invalidFile = new MockMultipartFile(
                "file",
                "not-image.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "not-an-image".getBytes()
        );

        mockMvc.perform(multipart(IMAGE_ENDPOINT)
                        .file(invalidFile)
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("올바른 이미지 파일이 아닙니다."));

        assertThat(fileCount(TEST_IMAGE_DIRECTORY)).isZero();
    }

    @Test
    void updatesExistingPlaceInformationAndLectureBuildingDetailsForAdmin()
            throws Exception {
        mockMvc.perform(put(ADMIN_PLACES_ENDPOINT + "/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "campusCode": "TAEAN",
                                  "placeName": "태안본관 별관",
                                  "latitude": 36.594600000,
                                  "longitude": 126.294100000,
                                  "category": "LECTURE_BUILDING",
                                  "oneLineDescription": "태안캠퍼스의 중심 강의 건물",
                                  "imageUrl": "https://images.example/taean-main.jpg",
                                  "lectureBuildingDetails": {
                                    "location": "태안캠퍼스 중앙",
                                    "floorCount": 5,
                                    "hasElevator": false,
                                    "operatingHours": "평일 08:00~21:00",
                                    "departments": ["항공운항학과", "항공교통물류학과"],
                                    "majorFacilities": ["대형강의실", "행정실"]
                                  }
                                }
                                """)
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeId").value(2))
                .andExpect(jsonPath("$.placeName").value("태안본관 별관"))
                .andExpect(jsonPath("$.category").value("LECTURE_BUILDING"))
                .andExpect(jsonPath("$.oneLineDescription")
                        .value("태안캠퍼스의 중심 강의 건물"))
                .andExpect(jsonPath("$.imageUrl")
                        .value("https://images.example/taean-main.jpg"))
                .andExpect(jsonPath("$.latitude").value(36.5946))
                .andExpect(jsonPath("$.longitude").value(126.2941))
                .andExpect(jsonPath("$.lectureBuildingDetails.location")
                        .value("태안캠퍼스 중앙"))
                .andExpect(jsonPath("$.lectureBuildingDetails.floorCount")
                        .value(5))
                .andExpect(jsonPath("$.lectureBuildingDetails.hasElevator")
                        .value(false))
                .andExpect(jsonPath(
                        "$.lectureBuildingDetails.departments[1]"
                ).value("항공교통물류학과"))
                .andExpect(jsonPath(
                        "$.lectureBuildingDetails.majorFacilities[1]"
                ).value("행정실"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT one_line_description FROM campus_places WHERE id = 2",
                String.class
        )).isEqualTo("태안캠퍼스의 중심 강의 건물");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT place_name_key FROM campus_places WHERE id = 2",
                String.class
        )).isEqualTo("태안본관별관");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM campus_lecture_building_departments WHERE place_id = 2",
                Integer.class
        )).isEqualTo(2);
    }

    @Test
    void createsLectureDetailsWhenExistingPlaceBecomesLectureBuilding()
            throws Exception {
        mockMvc.perform(put(ADMIN_PLACES_ENDPOINT + "/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "campusCode": "SEOSAN",
                                  "placeName": "새 강의 건물",
                                  "latitude": 36.690100000,
                                  "longitude": 126.580100000,
                                  "category": "LECTURE_BUILDING",
                                  "oneLineDescription": "새로 분류한 강의 건물",
                                  "imageUrl": "https://images.example/new-building.jpg",
                                  "lectureBuildingDetails": {
                                    "location": "서산캠퍼스",
                                    "floorCount": 3,
                                    "hasElevator": true,
                                    "operatingHours": "평일 09:00~18:00",
                                    "departments": ["컴퓨터공학과"],
                                    "majorFacilities": ["강의실"]
                                  }
                                }
                                """)
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("LECTURE_BUILDING"))
                .andExpect(jsonPath("$.lectureBuildingDetails.floorCount")
                        .value(3));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM campus_lecture_building_details WHERE place_id = 3",
                Integer.class
        )).isEqualTo(1);
    }

    @Test
    void removesLectureDetailsWhenPlaceChangesToAnotherCategory()
            throws Exception {
        mockMvc.perform(put(ADMIN_PLACES_ENDPOINT + "/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "campusCode": "TAEAN",
                                  "placeName": "태안본관",
                                  "latitude": 36.594581000,
                                  "longitude": 126.294056000,
                                  "category": "CAFE",
                                  "oneLineDescription": "태안캠퍼스 카페",
                                  "imageUrl": "https://images.example/taean-cafe.jpg"
                                }
                                """)
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("CAFE"))
                .andExpect(jsonPath("$.lectureBuildingDetails").doesNotExist());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM campus_lecture_building_details WHERE place_id = 2",
                Integer.class
        )).isZero();
    }

    @Test
    void validatesCategorySpecificInformationAndProtectsUpdateWithAdminRole()
            throws Exception {
        String cafeWithLectureDetails = """
                {
                  "campusCode": "SEOSAN",
                  "placeName": "가배앤빈",
                  "latitude": 36.691166000,
                  "longitude": 126.574659000,
                  "category": "CAFE",
                  "oneLineDescription": "잘못된 요청",
                  "imageUrl": "https://images.example/invalid.jpg",
                  "lectureBuildingDetails": {
                    "location": "서산캠퍼스",
                    "floorCount": 1,
                    "hasElevator": false,
                    "operatingHours": "09:00~18:00",
                    "departments": ["학과"],
                    "majorFacilities": ["시설"]
                  }
                }
                """;

        mockMvc.perform(put(ADMIN_PLACES_ENDPOINT + "/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cafeWithLectureDetails)
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "강의실 이외의 카테고리에는 lectureBuildingDetails를 입력할 수 없습니다."
                ));

        mockMvc.perform(put(ADMIN_PLACES_ENDPOINT + "/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cafeWithLectureDetails))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put(ADMIN_PLACES_ENDPOINT + "/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cafeWithLectureDetails)
                        .with(jwt().authorities(
                                new SimpleGrantedAuthority("ROLE_USER")
                        )))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsNotFoundWhenAdminUpdatesUnknownPlace() throws Exception {
        mockMvc.perform(put(ADMIN_PLACES_ENDPOINT + "/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "campusCode": "SEOSAN",
                                  "placeName": "존재하지 않는 장소",
                                  "latitude": 36.690000000,
                                  "longitude": 126.580000000,
                                  "category": "CAFE",
                                  "oneLineDescription": "존재하지 않는 장소",
                                  "imageUrl": "https://images.example/missing.jpg"
                                }
                                """)
                        .with(adminJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.path")
                        .value(ADMIN_PLACES_ENDPOINT + "/999"));
    }

    @Test
    void createsPlaceAndGeneratesInternalNameKeyForAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post(ADMIN_PLACES_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "campusCode": "SEOSAN",
                                  "placeName": " 새 카페 ",
                                  "latitude": 36.690700000,
                                  "longitude": 126.580700000,
                                  "category": "CAFE",
                                  "oneLineDescription": "새로 등록한 카페",
                                  "imageUrl": "https://images.example/new-cafe.jpg"
                                }
                                """)
                        .with(adminJwt()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.placeName").value("새 카페"))
                .andExpect(jsonPath("$.category").value("CAFE"))
                .andReturn();

        Number placeId = JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.placeId"
        );
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
                .isEqualTo("/api/campus-map/places/" + placeId.longValue());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT place_name_key FROM campus_places WHERE id = ?",
                String.class,
                placeId.longValue()
        )).isEqualTo("새카페");
    }

    @Test
    void rejectsDuplicatePlaceNameKeyWithinSameCampus() throws Exception {
        mockMvc.perform(post(ADMIN_PLACES_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "campusCode": "SEOSAN",
                                  "placeName": "가배 앤빈",
                                  "latitude": 36.691200000,
                                  "longitude": 126.574700000,
                                  "category": "CAFE",
                                  "oneLineDescription": "중복 장소",
                                  "imageUrl": "https://images.example/duplicate.jpg"
                                }
                                """)
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "같은 캠퍼스에 같은 이름의 장소가 이미 있습니다."
                ));
    }

    @Test
    void deletesPlaceAndLectureBuildingDetailsForAdmin() throws Exception {
        mockMvc.perform(delete(ADMIN_PLACES_ENDPOINT + "/2")
                        .with(adminJwt()))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM campus_places WHERE id = 2",
                Integer.class
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM campus_lecture_building_details WHERE place_id = 2",
                Integer.class
        )).isZero();

        mockMvc.perform(get(PLACES_ENDPOINT + "/2"))
                .andExpect(status().isNotFound());
    }

    @Test
    void exposesPlaceAndImageEndpointsInOpenApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/campus-map/places'].get")
                        .exists())
                .andExpect(jsonPath(
                        "$.paths['/api/campus-map/places/{placeId}'].get"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/admin/campus-map/place-images'].post"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/admin/campus-map/places/{placeId}'].put"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/admin/campus-map/places/{placeId}'].delete"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/admin/campus-map/places'].post"
                ).exists());
    }

    private void insertPlace(
            long id,
            String campusCode,
            String placeName,
            String placeNameKey,
            String category,
            String oneLineDescription,
            String imageUrl,
            String latitude,
            String longitude
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO campus_places (
                            id,
                            campus_code,
                            place_name,
                            place_name_key,
                            category,
                            one_line_description,
                            image_url,
                            latitude,
                            longitude,
                            created_at,
                            updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                id,
                campusCode,
                placeName,
                placeNameKey,
                category,
                oneLineDescription,
                imageUrl,
                latitude,
                longitude
        );
    }

    private MockMultipartFile pngFile(String originalFileName) {
        return new MockMultipartFile(
                "file",
                originalFileName,
                MediaType.IMAGE_PNG_VALUE,
                TINY_PNG
        );
    }

    private void cleanCampusPlaceTables() {
        jdbcTemplate.update("DELETE FROM campus_lecture_building_facilities");
        jdbcTemplate.update("DELETE FROM campus_lecture_building_departments");
        jdbcTemplate.update("DELETE FROM campus_lecture_building_details");
        jdbcTemplate.update("DELETE FROM campus_places");
    }

    private long fileCount(Path directory) throws Exception {
        if (!Files.exists(directory)) {
            return 0;
        }
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile).count();
        }
    }

    private void deleteUploadedImages() throws Exception {
        if (!Files.exists(TEST_IMAGE_DIRECTORY)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(TEST_IMAGE_DIRECTORY)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
