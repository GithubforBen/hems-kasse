package de.hems.kasse.payments;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final EpcQrService qr;

    public PaymentController(EpcQrService qr) {
        this.qr = qr;
    }

    @GetMapping("/epc-qr.png")
    public ResponseEntity<byte[]> qr(@RequestParam int amountCents,
                                     @RequestParam(required = false) String ref,
                                     @RequestParam(required = false, defaultValue = "512") int size) {
        if (amountCents <= 0) throw new ResponseStatusException(BAD_REQUEST, "amountCents must be > 0");
        try {
            byte[] png = qr.renderPng(amountCents, ref, size);
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .cacheControl(CacheControl.noStore())
                    .body(png);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(BAD_REQUEST, e.getMessage());
        } catch (IOException e) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "QR rendering failed");
        }
    }

    @GetMapping(value = "/epc-payload", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> payload(@RequestParam int amountCents,
                                          @RequestParam(required = false) String ref) {
        if (amountCents <= 0) throw new ResponseStatusException(BAD_REQUEST, "amountCents must be > 0");
        try {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(qr.payload(amountCents, ref));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(BAD_REQUEST, e.getMessage());
        }
    }
}
