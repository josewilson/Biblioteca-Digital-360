package br.com.biblioteca.loan;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import br.com.biblioteca.security.SecurityConfig;

@WebMvcTest(LoanController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.jwt.secret=segredo-de-teste-com-mais-de-32-caracteres")
class LoanControllerSecurityTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    LoanService service;

    private static RequestPostProcessor as(String role) {
        return jwt().jwt(j -> j.subject("7")).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void alunoNaoOperaOBalcao() throws Exception {
        mvc.perform(post("/api/v1/loans").with(as("STUDENT")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/loans/1/return").with(as("STUDENT"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/loans/1/renew").with(as("STUDENT"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/loans").with(as("STUDENT"))).andExpect(status().isForbidden());
    }

    @Test
    void alunoVeSeusProprioEmprestimos() throws Exception {
        when(service.list(any(), any(Boolean.class), any())).thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get("/api/v1/me/loans").with(as("STUDENT"))).andExpect(status().isOk());
    }

    @Test
    void bibliotecarioComPedidoIncompletoRecebe400() throws Exception {
        mvc.perform(post("/api/v1/loans").with(as("LIBRARIAN")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void semTokenRecebe401() throws Exception {
        mvc.perform(get("/api/v1/me/loans")).andExpect(status().isUnauthorized());
    }
}
