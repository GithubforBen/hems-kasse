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

    // EPC remittance field tops out at 140 chars; clamp QR / image size to a sensible
    // window so callers can't request absurd allocations.
    private static final int REF_MAX = 140;
    private static final int SIZE_MIN = 128;
    private static final int SIZE_MAX = 1024;
    private static final int AMOUNT_MAX_CENTS = 99_999_999;

    @GetMapping("/epc-qr.png")
    public ResponseEntity<byte[]> qr(@RequestParam int amountCents,
                                     @RequestParam(required = false) String ref,
                                     @RequestParam(required = false, defaultValue = "512") int size) {
        validateAmount(amountCents);
        validateRef(ref);
        int clamped = Math.max(SIZE_MIN, Math.min(SIZE_MAX, size));
        try {
            byte[] png = qr.renderPng(amountCents, ref, clamped);
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
        validateAmount(amountCents);
        validateRef(ref);
        try {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(qr.payload(amountCents, ref));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(BAD_REQUEST, e.getMessage());
        }
    }

    private static void validateAmount(int amountCents) {
        if (amountCents <= 0) throw new ResponseStatusException(BAD_REQUEST, "amountCents must be > 0");
        if (amountCents > AMOUNT_MAX_CENTS) throw new ResponseStatusException(BAD_REQUEST, "amountCents too large");
    }

    private static void validateRef(String ref) {
        if (ref != null && ref.length() > REF_MAX) {
            throw new ResponseStatusException(BAD_REQUEST, "ref too long (max " + REF_MAX + ")");
        }
    }
}
