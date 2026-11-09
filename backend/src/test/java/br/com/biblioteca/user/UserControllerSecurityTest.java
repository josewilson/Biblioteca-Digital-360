package br.com.biblioteca.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.com.biblioteca.security.SecurityConfig;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.jwt.secret=segredo-de-teste-com-mais-de-32-caracteres")
class UserControllerSecurityTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    UserService service;

    @Test
    void respostasDaApiVemComCabecalhosDeSeguranca() throws Exception {
        when(service.list(any(), any())).thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/v1/users").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_LIBRARIAN"))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }

    @Test
    void semTokenRecebe401() throws Exception {
        mvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void alunoRecebe403() throws Exception {
        mvc.perform(get("/api/v1/users").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void bibliotecarioListaUsuarios() throws Exception {
        Page<UserResponse> vazia = new PageImpl<>(List.of());
        when(service.list(any(), any())).thenReturn(vazia);
        mvc.perform(get("/api/v1/users").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_LIBRARIAN"))))
                .andExpect(status().isOk());
    }

    @Test
    void bibliotecarioNaoDesativaUsuario() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/users/5")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_LIBRARIAN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void alunoCadastrandoComCorpoInvalidoRecebe403EnaoAsMensagensDeValidacao() throws Exception {
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void cadastroComDadosInvalidosRecebe400() throws Exception {
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"\",\"email\":\"nao-e-email\",\"password\":\"123\",\"roles\":[]}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN_INSTITUTION"))))
                .andExpect(status().isBadRequest());
    }
}
