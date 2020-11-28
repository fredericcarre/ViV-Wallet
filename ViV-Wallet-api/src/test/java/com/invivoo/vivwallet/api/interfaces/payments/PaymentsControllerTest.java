package com.invivoo.vivwallet.api.interfaces.payments;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invivoo.vivwallet.api.ViVWalletApiApplication;
import com.invivoo.vivwallet.api.application.security.JWTTokenProvider;
import com.invivoo.vivwallet.api.application.security.SecurityService;
import com.invivoo.vivwallet.api.domain.action.Action;
import com.invivoo.vivwallet.api.domain.action.ActionService;
import com.invivoo.vivwallet.api.domain.payment.Payment;
import com.invivoo.vivwallet.api.domain.payment.PaymentService;
import com.invivoo.vivwallet.api.domain.user.User;
import com.invivoo.vivwallet.api.domain.user.UserService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@WebMvcTest(PaymentsController.class)
public class PaymentsControllerTest {

    private static final Action ACTION_1 = Action.builder().id(1L).viv(BigDecimal.TEN).build();
    private static final Action ACTION_2 = Action.builder().id(2L).viv(BigDecimal.TEN).build();
    private static final List<Action> ACTIONS = Arrays.asList(ACTION_1, ACTION_2);
    private static final LocalDate PAYMENT_DATE = LocalDate.of(2020, 1, 1);
    private static final User THEOPHILE_MONTGOMERY = User.builder().id(1L).fullName("Théophile MONTGOMERY").build();
    public static final User RECEIVER = User.builder()
                                            .id(8L)
                                            .fullName("John DOE")
                                            .build();
    private static final Payment PAYMENT_1 = Payment.builder().id(1L).creator(THEOPHILE_MONTGOMERY).receiver(RECEIVER).date(PAYMENT_DATE).actions(ACTIONS).build();
    private static final Payment PAYMENT_2 = Payment.builder().id(2L).creator(THEOPHILE_MONTGOMERY).receiver(RECEIVER).date(PAYMENT_DATE).actions(ACTIONS).build();
    private static final List<Payment> PAYMENTS = Arrays.asList(PAYMENT_1, PAYMENT_2);
    private static final PaymentRequest PAYMENT_REQUEST = PaymentRequest.builder()
                                                                        .date(PAYMENT_DATE)
                                                                        .receiverId(RECEIVER.getId())
                                                                        .actionIds(ACTIONS.stream().map(Action::getId).collect(Collectors.toList()))
                                                                        .build();
    private static final ObjectMapper mapper = new ViVWalletApiApplication().objectMapper();


    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JWTTokenProvider jwtTokenProvider;

    @MockBean
    private PaymentService paymentService;

    @MockBean
    private ActionService actionService;

    @MockBean
    private UserService userService;

    @MockBean
    private SecurityService securityService;

    @Test
    public void getAllPayments() throws Exception {
        // given
        when(paymentService.getAll())
                .thenReturn(PAYMENTS);

        // when
        ResultActions resultActions = this.mockMvc.perform(
                get("/api/v1/payments"))
                .andDo(MockMvcResultHandlers.print());
        // then
        resultActions.andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.content().string(mapper.writeValueAsString(PAYMENTS.stream()
                                                                                                    .map(PaymentDto::createFromPayment)
                                                                                                    .collect(Collectors.toList()))));
    }

    @Test
    public void getActionsByPaymentId() throws Exception {
        // given
        when(paymentService.getActionsByPaymentId(anyLong()))
                .thenReturn(ACTIONS);
        String jsonActions = mapper.writeValueAsString(ACTIONS);

        // when
        ResultActions resultActions = this.mockMvc.perform(
                get(String.format("/api/v1/payments/%s/actions", PAYMENT_1.getId())))
                .andDo(MockMvcResultHandlers.print());
        // then
        resultActions.andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.content().string(jsonActions));
    }

    @Test
    @WithMockUser(username = "Théophile MONTGOMERY", authorities = { "COMPANY_ADMINISTRATOR"})
    public void shouldReturnOKAndCreatedPayment_whenPostPaymentRequest() throws Exception {

        // given
        when(userService.findById(PAYMENT_REQUEST.getReceiverId())).thenReturn(Optional.of(RECEIVER));
        when(securityService.getConnectedUser()).thenReturn(Optional.of(THEOPHILE_MONTGOMERY));
        when(actionService.findAllById(ACTIONS.stream().map(Action::getId).collect(Collectors.toList()))).thenReturn(ACTIONS);
        when(paymentService.save(any(Payment.class))).thenReturn(PAYMENT_1);

        // when
        ResultActions resultActions = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
                                                                            .contentType(MediaType.APPLICATION_JSON_UTF8)
                                                                            .content(mapper.writeValueAsString(PAYMENT_REQUEST)))
                                             .andDo(MockMvcResultHandlers.print());
        // then
        verify(paymentService, Mockito.times(1)).save(PAYMENT_1.toBuilder().id(null).build());
        resultActions.andExpect(MockMvcResultMatchers.status().isCreated())
                     .andExpect(MockMvcResultMatchers.redirectedUrl(String.format("/api/v1/payments/%s", PAYMENT_1.getId())))
                     .andExpect(MockMvcResultMatchers.content().string(mapper.writeValueAsString(PaymentDto.createFromPayment(PAYMENT_1))));

    }

    @Test
    @WithAnonymousUser
    public void shouldReturnUnauthorized_whenPostPaymentRequestWithUserWithoutRequiredAuthorities() throws Exception {
        // given
        when(userService.findById(PAYMENT_REQUEST.getReceiverId())).thenReturn(Optional.of(RECEIVER));
        when(securityService.getConnectedUser()).thenReturn(Optional.of(THEOPHILE_MONTGOMERY));
        when(actionService.findAllById(ACTIONS.stream().map(Action::getId).collect(Collectors.toList()))).thenReturn(ACTIONS);
        when(paymentService.save(any(Payment.class))).thenReturn(PAYMENT_1);

        // when
        ResultActions resultActions = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
                                                                            .contentType(MediaType.APPLICATION_JSON_UTF8)
                                                                            .content(mapper.writeValueAsString(PAYMENT_REQUEST)))
                                             .andDo(MockMvcResultHandlers.print());
        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isForbidden());
    }
}
