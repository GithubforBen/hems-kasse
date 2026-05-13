package de.hems.kasse.payments;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

@Service
public class EpcQrService {

    private final EpcPayloadBuilder builder;

    public EpcQrService(EpcPayloadBuilder builder) {
        this.builder = builder;
    }

    public byte[] renderPng(int amountCents, String extraRemittance, int size) throws IOException {
        String payload = builder.build(amountCents, extraRemittance);
        int dim = Math.max(128, Math.min(size, 1024));

        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M); // EPC recommendation
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 4); // EPC069-12 requires ≥4 module quiet zone

        try {
            BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, dim, dim, hints);
            ByteArrayOutputStream baos = new ByteArrayOutputStream(8192);
            MatrixToImageWriter.writeToStream(matrix, "PNG", baos);
            return baos.toByteArray();
        } catch (WriterException e) {
            throw new IOException("QR encoding failed", e);
        }
    }

    public String payload(int amountCents, String extraRemittance) {
        return builder.build(amountCents, extraRemittance);
    }
}
