package com.billetterie.ticket_service.ticket;

import com.billetterie.ticket_service.dto.ReserveRequest;
import com.billetterie.ticket_service.dto.TicketResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping("/reserve")
    public ResponseEntity<TicketResponse> reserve(@AuthenticationPrincipal Jwt jwt,
                                                  @Valid @RequestBody ReserveRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        Ticket ticket = ticketService.reserve(request.eventId(), userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(TicketResponse.from(ticket));
    }

    @GetMapping("/my-tickets")
    public List<TicketResponse> myTickets(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ticketService.getMyTickets(userId).stream().map(TicketResponse::from).toList();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        ticketService.cancel(id, userId);
        return ResponseEntity.noContent().build();
    }
}