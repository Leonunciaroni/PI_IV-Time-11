package br.edu.puccampinas.fastattendance.api.seguranca;

import br.edu.puccampinas.fastattendance.common.seguranca.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.Duration;

/**
 * API sem estado: cada requisição se autentica pelo JWT no header {@code Authorization}.
 * Apenas login, cadastro e health check são públicos; as rotas de turmas são só do professor.
 */
@Configuration
public class SegurancaConfig {

    @Bean
    JwtService jwtService(JwtProperties propriedades) {
        return new JwtService(propriedades.segredo(), Duration.ofHours(propriedades.validadeHoras()));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain filtros(HttpSecurity http, JwtAuthFilter jwtAuthFilter,
                                RespostaNaoAutenticado naoAutenticado,
                                RespostaAcessoNegado acessoNegado) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/registro").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/error").permitAll()
                        .requestMatchers("/api/turmas/**").hasRole("PROFESSOR")
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(naoAutenticado)
                        .accessDeniedHandler(acessoNegado))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
