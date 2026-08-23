package hsu.hanseomate.domain.busschedule.dto;

import hsu.hanseomate.domain.busschedule.entity.BusSchedule;
import hsu.hanseomate.domain.busschedule.type.MainCategory;
import hsu.hanseomate.domain.busschedule.type.SubCategory;
import java.time.LocalDateTime;

public record BusScheduleResponse(
        Long id,
        MainCategory mainCategory,
        SubCategory subCategory,
        String imageUrl,
        LocalDateTime updatedAt
) {

    public static BusScheduleResponse from(BusSchedule busSchedule) {
        return new BusScheduleResponse(
                busSchedule.getId(),
                busSchedule.getMainCategory(),
                busSchedule.getSubCategory(),
                busSchedule.getImageUrl(),
                busSchedule.getUpdatedAt()
        );
    }
}
