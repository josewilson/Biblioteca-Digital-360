package br.com.biblioteca.loan;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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

@WebMvcTest({ReservationController.class, FineController.class})
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.jwt.secret=segredo-de-teste-com-mais-de-32-caracteres")
class CirculacaoSecurityTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ReservationService reservations;

    @MockitoBean
    FineService fines;

    private static RequestPostProcessor as(String role) {
        return jwt().jwt(j -> j.subject("7")).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Test
    void alunoNaoOperaReservasDeOutrosNemRecebePagamento() throws Exception {
        mvc.perform(post("/api/v1/reservations").with(as("STUDENT")).contentType(JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/reservations").with(as("STUDENT"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/fines/1/payments").with(as("STUDENT")).contentType(JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/fines").with(as("STUDENT"))).andExpect(status().isForbidden());
    }

    @Test
    void alunoReservaEVeSuasMultasEReservas() throws Exception {
        when(reservations.list(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));
        when(fines.list(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/v1/me/reservations").with(as("STUDENT"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/me/fines").with(as("STUDENT"))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/me/reservations").with(as("STUDENT")).contentType(JSON).content("{}"))
                .andExpect(status().isBadRequest());   // chegou ao controller: faltou bookId
    }

    @Test
    void auxiliarRecebePagamentoMasNaoIsenta() throws Exception {
        when(fines.pay(any(), any(), any(), any())).thenReturn(new PaymentResponse(1L, 5L, 1L, 1L,
                new BigDecimal("3.00"), PaymentMethod.PIX, 7L, null));

        mvc.perform(post("/api/v1/fines/5/payments").with(as("ASSISTANT")).contentType(JSON)
                .content("{\"method\":\"PIX\"}")).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/fines/5/waive").with(as("ASSISTANT")).contentType(JSON)
                .content("{\"reason\":\"motivo\"}")).andExpect(status().isForbidden());
    }

    @Test
    void bibliotecarioIsentaMasPrecisaDeJustificativa() throws Exception {
        mvc.perform(post("/api/v1/fines/5/waive").with(as("LIBRARIAN")).contentType(JSON)
                .content("{\"reason\":\"  \"}")).andExpect(status().isBadRequest());
    }

    @Test
    void alunoComCorpoInvalidoNaIsencaoRecebe403() throws Exception {
        mvc.perform(post("/api/v1/fines/5/waive").with(as("STUDENT")).contentType(JSON).content("{}"))
                .andExpect(status().isForbidden());
    }
}
