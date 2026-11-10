package br.com.biblioteca.acervo;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.util.Iterator;
import java.util.Set;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.biblioteca.audit.AuditService;
import br.com.biblioteca.shared.error.BusinessRuleException;
import br.com.biblioteca.shared.error.ResourceNotFoundException;

/**
 * Capa da obra enviada pelo operador. O arquivo enviado nunca é guardado nem servido como veio: é decodificado e
 * regravado como JPEG em dois tamanhos (ampliada e miniatura). Isso derruba qualquer conteúdo escondido no arquivo
 * (metadados, scripts, arquivo "poliglota") e padroniza o tamanho. Os bytes ficam em {@code book_covers}, longe da
 * tabela de obras, para as listagens não os carregarem.
 */
@Service
public class BookCoverService {

    static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final int MAX_SIDE = 8000;
    private static final long MAX_PIXELS = 25_000_000L;
    private static final Set<String> FORMATS = Set.of("jpeg", "png");
    private static final int[] FULL = {600, 900};    // largura x altura máximas
    private static final int[] THUMB = {160, 240};

    private final BookRepository books;
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final Clock clock;

    BookCoverService(BookRepository books, JdbcTemplate jdbc, AuditService audit, Clock clock) {
        this.books = books;
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public BookResponse store(Long bookId, byte[] data, Long callerId, String ip) {
        Book book = findBook(bookId);
        BufferedImage image = decode(data);
        byte[] full = encode(scaleToFit(image, FULL));
        byte[] thumb = encode(scaleToFit(image, THUMB));
        long version = clock.millis();
        jdbc.update("""
                insert into book_covers (book_id, full_image, thumb_image) values (?, ?, ?)
                on conflict (book_id) do update set full_image = excluded.full_image, thumb_image = excluded.thumb_image""",
                bookId, full, thumb);
        book.setCoverVersion(version);
        audit.record(callerId, "BOOK_COVER_SET", "book", bookId, null, ip);
        return BookResponse.from(book);
    }

    @Transactional
    public BookResponse remove(Long bookId, Long callerId, String ip) {
        Book book = findBook(bookId);
        jdbc.update("delete from book_covers where book_id = ?", bookId);
        book.setCoverVersion(null);
        audit.record(callerId, "BOOK_COVER_REMOVED", "book", bookId, null, ip);
        return BookResponse.from(book);
    }

    /** @return JPEG da capa ({@code thumb} = miniatura) */
    @Transactional(readOnly = true)
    public byte[] image(Long bookId, boolean thumb) {
        var rows = jdbc.queryForList("select " + (thumb ? "thumb_image" : "full_image") + " as img from book_covers where book_id = ?", bookId);
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Esta obra não tem capa enviada.");
        }
        return (byte[]) rows.get(0).get("img");
    }

    private Book findBook(Long id) {
        return books.findById(id).orElseThrow(() -> new ResourceNotFoundException("Obra " + id + " não existe."));
    }

    /** Confere formato e dimensões ANTES de decodificar os pixels (imagem minúscula em bytes pode ter bilhões de pixels). */
    private static BufferedImage decode(byte[] data) {
        if (data == null || data.length == 0) {
            throw new BusinessRuleException("Escolha uma imagem.");
        }
        if (data.length > MAX_BYTES) {
            throw new BusinessRuleException("A imagem passa de 2 MB.");
        }
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw invalid();
            }
            ImageReader reader = readers.next();
            try {
                if (!FORMATS.contains(reader.getFormatName().toLowerCase())) {
                    throw invalid();
                }
                reader.setInput(in, true, true);
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                if (w < 1 || h < 1 || w > MAX_SIDE || h > MAX_SIDE || (long) w * h > MAX_PIXELS) {
                    throw new BusinessRuleException("A imagem é grande demais (máximo 8000 px de lado).");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof BusinessRuleException b) {
                throw b;
            }
            throw invalid();
        }
    }

    private static BusinessRuleException invalid() {
        return new BusinessRuleException("Envie uma imagem JPEG ou PNG válida.");
    }

    /** Cabe na caixa sem distorcer e sem ampliar; fundo branco no lugar da transparência. */
    private static BufferedImage scaleToFit(BufferedImage src, int[] box) {
        double scale = Math.min(1.0, Math.min((double) box[0] / src.getWidth(), (double) box[1] / src.getHeight()));
        int w = Math.max(1, (int) Math.round(src.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(src.getHeight() * scale));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static byte[] encode(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             MemoryCacheImageOutputStream out = new MemoryCacheImageOutputStream(bytes)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.85f);
            writer.setOutput(out);
            writer.write(null, new IIOImage(image, null, null), param);
            out.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            writer.dispose();
        }
    }
}
