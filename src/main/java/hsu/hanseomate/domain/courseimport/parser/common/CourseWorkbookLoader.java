package hsu.hanseomate.domain.courseimport.parser.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Loads OOXML workbooks after resolving Hancom compatibility style wrappers. */
public final class CourseWorkbookLoader {

    private static final String STYLES_PATH = "xl/styles.xml";
    private static final int MAX_ARCHIVE_ENTRIES = 10_000;
    private static final long MAX_UNCOMPRESSED_BYTES = 100L * 1024L * 1024L;
    private static final int COPY_BUFFER_SIZE = 8192;
    private static final String MC_NAMESPACE =
            "http://schemas.openxmlformats.org/markup-compatibility/2006";
    private static final Set<String> COUNTED_STYLE_COLLECTIONS = Set.of(
            "numFmts", "fonts", "fills", "borders", "cellStyleXfs",
            "cellXfs", "cellStyles", "dxfs"
    );

    public Workbook load(byte[] fileBytes) {
        try {
            byte[] compatible = makeOpenXmlCompatible(fileBytes);
            return new XSSFWorkbook(new ByteArrayInputStream(compatible));
        } catch (CourseWorkbookParseException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CourseWorkbookParseException(
                    "WORKBOOK_OPEN_FAILED",
                    "엑셀 파일을 열 수 없습니다.",
                    Map.of("reason", exception.getClass().getSimpleName()),
                    exception
            );
        }
    }

    byte[] makeOpenXmlCompatible(byte[] fileBytes) {
        try {
            List<ZipPart> parts = readParts(fileBytes);
            boolean changed = false;
            List<ZipPart> output = new ArrayList<>(parts.size());
            for (ZipPart part : parts) {
                if (!STYLES_PATH.equals(part.name())) {
                    output.add(part);
                    continue;
                }
                XmlCompatibilityResult result = unwrapAlternateContent(part.payload());
                output.add(new ZipPart(part.name(), result.payload(), part.time()));
                changed |= result.changed();
            }
            return changed ? writeParts(output) : fileBytes;
        } catch (CourseWorkbookParseException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CourseWorkbookParseException(
                    "WORKBOOK_OPEN_FAILED",
                    "엑셀 호환성 정보를 처리할 수 없습니다.",
                    Map.of("reason", exception.getClass().getSimpleName()),
                    exception
            );
        }
    }

    private static List<ZipPart> readParts(byte[] fileBytes) throws IOException {
        List<ZipPart> parts = new ArrayList<>();
        long totalUncompressedBytes = 0;
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(fileBytes))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (parts.size() >= MAX_ARCHIVE_ENTRIES) {
                    throw archiveLimitExceeded("entryCount", parts.size() + 1L, MAX_ARCHIVE_ENTRIES);
                }
                ByteArrayOutputStream payload = new ByteArrayOutputStream();
                byte[] buffer = new byte[COPY_BUFFER_SIZE];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    totalUncompressedBytes += read;
                    if (totalUncompressedBytes > MAX_UNCOMPRESSED_BYTES) {
                        throw archiveLimitExceeded(
                                "uncompressedBytes",
                                totalUncompressedBytes,
                                MAX_UNCOMPRESSED_BYTES
                        );
                    }
                    payload.write(buffer, 0, read);
                }
                parts.add(new ZipPart(entry.getName(), payload.toByteArray(), entry.getTime()));
                input.closeEntry();
            }
        }
        return parts;
    }

    private static CourseWorkbookParseException archiveLimitExceeded(
            String limitType,
            long actual,
            long maximum
    ) {
        return new CourseWorkbookParseException(
                "WORKBOOK_ARCHIVE_TOO_LARGE",
                "압축 해제된 엑셀 파일이 허용 범위를 초과했습니다.",
                Map.of("limitType", limitType, "actual", actual, "max", maximum)
        );
    }

    private static byte[] writeParts(List<ZipPart> parts) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (ZipPart part : parts) {
                ZipEntry entry = new ZipEntry(part.name());
                if (part.time() >= 0) {
                    entry.setTime(part.time());
                }
                zip.putNextEntry(entry);
                zip.write(part.payload());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private static XmlCompatibilityResult unwrapAlternateContent(byte[] xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));

        int recovered = unwrapRecursively(document.getDocumentElement());
        if (recovered == 0) {
            return new XmlCompatibilityResult(xml, false);
        }
        refreshCounts(document.getDocumentElement());

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(document), new StreamResult(output));
        return new XmlCompatibilityResult(output.toByteArray(), true);
    }

    private static int unwrapRecursively(Node parent) {
        int recovered = 0;
        for (int index = parent.getChildNodes().getLength() - 1; index >= 0; index--) {
            Node child = parent.getChildNodes().item(index);
            if (!isElement(child, MC_NAMESPACE, "AlternateContent")) {
                recovered += unwrapRecursively(child);
                continue;
            }
            Element fallback = null;
            NodeList children = child.getChildNodes();
            for (int childIndex = 0; childIndex < children.getLength(); childIndex++) {
                Node candidate = children.item(childIndex);
                if (isElement(candidate, MC_NAMESPACE, "Fallback")) {
                    fallback = (Element) candidate;
                    break;
                }
            }
            if (fallback == null) {
                continue;
            }
            List<Node> replacements = new ArrayList<>();
            for (int childIndex = 0; childIndex < fallback.getChildNodes().getLength(); childIndex++) {
                Node candidate = fallback.getChildNodes().item(childIndex);
                if (candidate.getNodeType() == Node.ELEMENT_NODE) {
                    replacements.add(candidate.cloneNode(true));
                }
            }
            if (replacements.isEmpty()) {
                continue;
            }
            for (Node replacement : replacements) {
                parent.insertBefore(replacement, child);
                recovered += unwrapRecursively(replacement);
            }
            parent.removeChild(child);
            recovered++;
        }
        return recovered;
    }

    private static void refreshCounts(Node node) {
        if (node.getNodeType() == Node.ELEMENT_NODE
                && COUNTED_STYLE_COLLECTIONS.contains(node.getLocalName())) {
            int elementCount = 0;
            for (int index = 0; index < node.getChildNodes().getLength(); index++) {
                if (node.getChildNodes().item(index).getNodeType() == Node.ELEMENT_NODE) {
                    elementCount++;
                }
            }
            ((Element) node).setAttribute("count", Integer.toString(elementCount));
        }
        for (int index = 0; index < node.getChildNodes().getLength(); index++) {
            refreshCounts(node.getChildNodes().item(index));
        }
    }

    private static boolean isElement(Node node, String namespace, String localName) {
        return node.getNodeType() == Node.ELEMENT_NODE
                && namespace.equals(node.getNamespaceURI())
                && localName.equals(node.getLocalName());
    }

    private record ZipPart(String name, byte[] payload, long time) {
    }

    private record XmlCompatibilityResult(byte[] payload, boolean changed) {
    }
}
