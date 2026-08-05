package com.peachwuhu.app.storage;

import com.peachwuhu.app.common.AppProperties;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.tiff.TiffField;
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputDirectory;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PhotoStorage {
    private static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4", "mov", "m4v", "webm", "3gp");
    private static final Pattern FILENAME_TIME =
        Pattern.compile(".*?(\\d{4})(\\d{2})(\\d{2})[_-]?(\\d{2})(\\d{2})(\\d{2}).*");
    private static final Pattern WECHAT_CAMERA_TIME =
        Pattern.compile("(?i)^wx_camera_(\\d{13})(?:\\D.*)?$");
    private static final DateTimeFormatter PHOTO_TIME_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Shanghai"));
    private final AppProperties properties;

    public PhotoStorage(AppProperties properties) {
        this.properties = properties;
    }

    public Path root() {
        return Path.of(properties.getStorageRoot()).toAbsolutePath().normalize();
    }

    public Path resolve(String relativePath) {
        String normalized = relativePath.replace('\\', '/');
        Path result = root().resolve(normalized).normalize();
        if (!result.startsWith(root())) throw new IllegalArgumentException("非法文件路径");
        return result;
    }

    public Resource resource(String relativePath) throws IOException {
        Path path = resolve(relativePath);
        if (!Files.isRegularFile(path)) throw new NoSuchFileException(relativePath);
        return new UrlResource(path.toUri());
    }

    public String sanitizeFilename(String original) {
        String base = Paths.get(original == null ? "unnamed.jpg" : original).getFileName().toString();
        return base.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
    }

    public boolean allowed(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0) return false;
        Set<String> allowed = Set.of(properties.getAllowedExtensions().toLowerCase(Locale.ROOT).split(","));
        return allowed.contains(filename.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    public boolean isVideo(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 && VIDEO_EXTENSIONS.contains(filename.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    public Path unique(Path directory, String filename) throws IOException {
        Files.createDirectories(directory);
        Path target = directory.resolve(filename);
        if (!Files.exists(target)) return target;
        String name = filename;
        String extension = "";
        int dot = filename.lastIndexOf('.');
        if (dot >= 0) {
            name = filename.substring(0, dot);
            extension = filename.substring(dot);
        }
        int suffix = 1;
        while (Files.exists(target)) target = directory.resolve(name + "_" + suffix++ + extension);
        return target;
    }

    public void createPreview(Path source, Path target) throws IOException {
        if (isVideo(source.getFileName().toString())) {
            createVideoPoster(source, target);
            return;
        }
        BufferedImage original = ImageIO.read(source.toFile());
        if (original == null) throw new IOException("文件不是有效图片或当前格式暂不支持");
        int limit = properties.getPreviewSize();
        double scale = Math.min(1d, Math.min((double) limit / original.getWidth(), (double) limit / original.getHeight()));
        int width = Math.max(1, (int) Math.round(original.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(original.getHeight() * scale));
        BufferedImage preview = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = preview.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        graphics.drawImage(original, 0, 0, width, height, null);
        graphics.dispose();
        Files.createDirectories(target.getParent());
        if (!ImageIO.write(preview, "jpg", target.toFile())) throw new IOException("无法生成缩略图");
    }

    private void createVideoPoster(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Process process = new ProcessBuilder(
            "ffmpeg", "-y", "-ss", "0.1", "-i", source.toString(), "-frames:v", "1",
            "-vf", "scale=min(" + properties.getPreviewSize() + "\\,iw):-2", "-q:v", "3", target.toString()
        ).redirectErrorStream(true).start();
        try {
            String output = new String(process.getInputStream().readAllBytes());
            if (process.waitFor() != 0 || !Files.isRegularFile(target)) {
                throw new IOException("视频封面生成失败：" + output.lines().reduce((a, b) -> b).orElse("FFmpeg错误"));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("视频封面生成被中断", exception);
        }
    }

    public VideoMetadata videoMetadata(Path source) {
        if (!isVideo(source.getFileName().toString())) return new VideoMetadata(0, 0, 0);
        try {
            Process process = new ProcessBuilder(
                "ffprobe", "-v", "error", "-select_streams", "v:0",
                "-show_entries", "stream=width,height:format=duration",
                "-of", "default=noprint_wrappers=1", source.toString()
            ).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes());
            if (process.waitFor() != 0) return new VideoMetadata(0, 0, 0);
            int width = 0, height = 0;
            long duration = 0;
            for (String line : output.lines().toList()) {
                String[] pair = line.split("=", 2);
                if (pair.length != 2) continue;
                if ("width".equals(pair[0])) width = Integer.parseInt(pair[1]);
                if ("height".equals(pair[0])) height = Integer.parseInt(pair[1]);
                if ("duration".equals(pair[0])) duration = Math.round(Double.parseDouble(pair[1]) * 1000);
            }
            return new VideoMetadata(duration, width, height);
        } catch (Exception ignored) {
            return new VideoMetadata(0, 0, 0);
        }
    }

    public record VideoMetadata(long durationMs, int width, int height) {}

    public String extractPhotoTime(Path source) {
        if (isVideo(source.getFileName().toString())) {
            String videoTime = extractVideoTime(source);
            if (!videoTime.isBlank()) return videoTime;
        }
        try {
            ImageMetadata metadata = Imaging.getMetadata(source.toFile());
            if (metadata instanceof JpegImageMetadata jpeg) {
                TiffField field = jpeg.findExifValue(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL);
                if (field == null) {
                    field = jpeg.findExifValue(ExifTagConstants.EXIF_TAG_DATE_TIME_DIGITIZED);
                }
                if (field != null) {
                    String value = normalizePhotoTime(field.getStringValue());
                    if (!value.isBlank()) return value;
                }
            }
        } catch (Exception ignored) {
            // Some valid formats (for example MPO/HEIC) are not decoded by Commons Imaging.
        }
        Matcher matcher = FILENAME_TIME.matcher(source.getFileName().toString());
        if (matcher.matches()) {
            return "%s-%s-%s %s:%s".formatted(
                matcher.group(1), matcher.group(2), matcher.group(3),
                matcher.group(4), matcher.group(5)
            );
        }
        Matcher wechatMatcher = WECHAT_CAMERA_TIME.matcher(source.getFileName().toString());
        if (wechatMatcher.matches()) {
            try {
                Instant value = Instant.ofEpochMilli(Long.parseLong(wechatMatcher.group(1)));
                int year = value.atZone(ZoneId.of("Asia/Shanghai")).getYear();
                int nextYear = Instant.now().atZone(ZoneId.of("Asia/Shanghai")).getYear() + 1;
                if (year >= 2000 && year <= nextYear) return PHOTO_TIME_FORMAT.format(value);
            } catch (RuntimeException ignored) {
                // Invalid timestamps are treated as unknown photo times.
            }
        }
        return "";
    }

    private String extractVideoTime(Path source) {
        try {
            Process process = new ProcessBuilder(
                "ffprobe", "-v", "error", "-show_entries",
                "format_tags=creation_time:stream_tags=creation_time", "-of", "default=noprint_wrappers=1",
                source.toString()
            ).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes());
            if (process.waitFor() != 0) return "";
            for (String line : output.lines().toList()) {
                int equals = line.indexOf('=');
                if (equals < 0) continue;
                String raw = line.substring(equals + 1).trim();
                try {
                    return PHOTO_TIME_FORMAT.format(OffsetDateTime.parse(raw).toInstant());
                } catch (RuntimeException ignored) {
                    String normalized = normalizePhotoTime(raw.replace('T', ' '));
                    if (!normalized.isBlank()) return normalized;
                }
            }
        } catch (Exception ignored) {
            // Filename parsing remains available when ffprobe metadata is absent.
        }
        return "";
    }

    public byte[] withPhotoTimeExif(Path source, String photoTime) throws IOException {
        byte[] original = Files.readAllBytes(source);
        if (photoTime == null || !photoTime.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}")) return original;
        String extension = source.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!extension.endsWith(".jpg") && !extension.endsWith(".jpeg")) return original;
        try {
            ImageMetadata metadata = Imaging.getMetadata(source.toFile());
            TiffOutputSet outputSet = new TiffOutputSet();
            if (metadata instanceof JpegImageMetadata jpeg) {
                if (jpeg.findExifValue(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL) != null) return original;
                if (jpeg.getExif() != null) outputSet = jpeg.getExif().getOutputSet();
            }
            TiffOutputDirectory exif = outputSet.getOrCreateExifDirectory();
            String exifTime = photoTime.substring(0, 10).replace('-', ':') + photoTime.substring(10) + ":00";
            exif.removeField(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL);
            exif.removeField(ExifTagConstants.EXIF_TAG_DATE_TIME_DIGITIZED);
            exif.add(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL, exifTime);
            exif.add(ExifTagConstants.EXIF_TAG_DATE_TIME_DIGITIZED, exifTime);
            ByteArrayOutputStream output = new ByteArrayOutputStream(original.length + 4096);
            new ExifRewriter().updateExifMetadataLossless(original, output, outputSet);
            return output.toByteArray();
        } catch (Exception ignored) {
            return original;
        }
    }

    private String normalizePhotoTime(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        if (value.matches("\\d{4}:\\d{2}:\\d{2} \\d{2}:\\d{2}(:\\d{2})?.*")) {
            return value.substring(0, 10).replace(':', '-') + value.substring(10, 16);
        }
        if (value.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}.*")) {
            return value.substring(0, 16);
        }
        return "";
    }

    public void move(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
}
