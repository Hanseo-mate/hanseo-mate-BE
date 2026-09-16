package hsu.hanseomate.domain.busschedule.entity;

import hsu.hanseomate.domain.busschedule.type.MainCategory;
import hsu.hanseomate.domain.busschedule.type.SubCategory;
import hsu.hanseomate.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "bus_schedules")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BusSchedule extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "main_category", nullable = false, length = 50)
    private MainCategory mainCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "sub_category", nullable = false, length = 100)
    private SubCategory subCategory;

    @Column(name = "image_url", nullable = false, length = 2048)
    private String imageUrl;

    @Column(name = "server_file_path", nullable = false, length = 512)
    private String serverFilePath;

    private BusSchedule(
            MainCategory mainCategory,
            SubCategory subCategory,
            String imageUrl,
            String serverFilePath
    ) {
        this.mainCategory = mainCategory;
        this.subCategory = subCategory;
        this.imageUrl = imageUrl;
        this.serverFilePath = serverFilePath;
    }

    public static BusSchedule create(
            MainCategory mainCategory,
            SubCategory subCategory,
            String imageUrl,
            String serverFilePath
    ) {
        return new BusSchedule(mainCategory, subCategory, imageUrl, serverFilePath);
    }

    public void update(String imageUrl, String serverFilePath) {
        this.imageUrl = imageUrl;
        this.serverFilePath = serverFilePath;
    }
}
