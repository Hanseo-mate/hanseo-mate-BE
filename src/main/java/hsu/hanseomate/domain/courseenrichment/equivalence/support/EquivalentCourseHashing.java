package hsu.hanseomate.domain.courseenrichment.equivalence.support;

import hsu.hanseomate.domain.courseenrichment.equivalence.dto.EquivalentCourseGroupData;
import hsu.hanseomate.domain.courseimport.parser.common.ExcelText;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

public final class EquivalentCourseHashing {

    private static final String MEMBER_SEPARATOR = "\u001e";
    private static final String GROUP_SEPARATOR = "\u001d";

    private EquivalentCourseHashing() {
    }

    public static String rawFileSha256(byte[] fileBytes) {
        return sha256(fileBytes);
    }

    public static String canonicalHash(List<EquivalentCourseGroupData> groups) {
        List<String> canonicalGroups = groups.stream()
                .map(group -> group.members().stream()
                        .map(member -> member.courseCode()
                                + "|"
                                + ExcelText.normalize(member.courseName()))
                        .sorted()
                        .reduce((left, right) -> left + MEMBER_SEPARATOR + right)
                        .orElse(""))
                .sorted(Comparator.naturalOrder())
                .toList();
        return sha256(String.join(GROUP_SEPARATOR, canonicalGroups)
                .getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value)
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", exception);
        }
    }
}
