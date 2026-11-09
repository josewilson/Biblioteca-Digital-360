package br.com.biblioteca.acervo;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.com.biblioteca.security.SecurityConfig;

@WebMvcTest(BookController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.jwt.secret=segredo-de-teste-com-mais-de-32-caracteres")
class BookControllerSecurityTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    BookService service;

    private static org.springframework.test.web.servlet.request.RequestPostProcessor as(String role) {
        return jwt().jwt(j -> j.subject("1")).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void semTokenRecebe401() throws Exception {
        mvc.perform(get("/api/v1/books")).andExpect(status().isUnauthorized());
    }

    @Test
    void alunoConsultaOAcervo() throws Exception {
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get("/api/v1/books").with(as("STUDENT"))).andExpect(status().isOk());
    }

    @Test
    void alunoNaoCadastraObraNemMudaStatusDeExemplar() throws Exception {
        mvc.perform(post("/api/v1/books").with(as("STUDENT")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/copies/1/status").with(as("STUDENT")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"BAIXADO\"}")).andExpect(status().isForbidden());
    }

    @Test
    void obraSemExemplaresRecebe400() throws Exception {
        mvc.perform(post("/api/v1/books").with(as("LIBRARIAN")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Dom Casmurro\",\"copies\":[]}")).andExpect(status().isBadRequest());
    }
}
