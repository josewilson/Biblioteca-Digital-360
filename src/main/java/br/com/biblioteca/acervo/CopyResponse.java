package br.com.biblioteca.acervo;

public record CopyResponse(Long id, String barcode, CopyKind kind, CopyStatus status,
                           CopyCondition condition, String location) {

    static CopyResponse from(BookCopy c) {
        return new CopyResponse(c.getId(), c.getBarcode(), c.getKind(), c.getStatus(), c.getCondition(),
                c.getLocation());
    }
}
