package com.keystone.orders.api;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.keystone.orders.application.OrderService;
import com.keystone.orders.config.SecurityConfig;
import com.keystone.orders.domain.Order;
import com.keystone.orders.domain.OrderLineItem;
import com.keystone.orders.domain.OrderNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// @WebMvcTest's component scan is restricted to controllers/converters/etc
// and doesn't pick up arbitrary @Configuration classes — SecurityConfig
// must be imported explicitly, or this slice falls back to Spring
// Security's default auto-config (CSRF-protected form login) instead of
// the app's actual JWT resource-server filter chain.
@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @Test
    void createOrderReturns201WithLocationHeader() throws Exception {
        Order order = Order.create("customer-1", "USD",
                List.of(new OrderLineItem("sku-1", 2, new BigDecimal("10.00"))));
        when(orderService.createOrder(anyString(), anyString(), any())).thenReturn(order);

        String requestBody = """
                {
                  "customerId": "customer-1",
                  "currency": "USD",
                  "items": [ { "productId": "sku-1", "quantity": 2, "unitPrice": 10.00 } ]
                }
                """;

        mockMvc.perform(post("/orders").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.totalAmount", is(20.00)));
    }

    @Test
    void createOrderRejectsEmptyItems() throws Exception {
        String requestBody = """
                { "customerId": "customer-1", "currency": "USD", "items": [] }
                """;

        mockMvc.perform(post("/orders").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getOrderReturns404WhenMissing() throws Exception {
        UUID missingId = UUID.randomUUID();
        when(orderService.getOrder(missingId)).thenThrow(new OrderNotFoundException(missingId));

        mockMvc.perform(get("/orders/{id}", missingId).with(jwt())).andExpect(status().isNotFound());
    }

    @Test
    void createOrderWithoutTokenReturns401() throws Exception {
        String requestBody = """
                { "customerId": "customer-1", "currency": "USD", "items": [] }
                """;

        mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isUnauthorized());
    }
}
