package hsu.hanseomate.domain.timetable.search.type;

public enum GeneralCategoryFilter {
    REQUIRED("교양필수"),
    AREA_1("1영역"),
    AREA_2("2영역"),
    AREA_3("3영역"),
    E_CLASS("e-Class"),
    HSU_CYBER("한서대학교 사이버강좌"),
    OCU("OCU"),
    CHUNGNAM_ELEARNING("충남 e러닝"),
    SDU("SDU"),
    OTHER("기타");

    private final String label;

    GeneralCategoryFilter(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
