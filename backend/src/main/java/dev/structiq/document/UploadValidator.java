package dev.structiq.document;

import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import dev.structiq.common.ApiException;

@Component
public class UploadValidator {
    public record Validated(String filename,String mimeType,byte[] bytes) {}
    private final long maxBytes;
    public UploadValidator(@Value("${structiq.upload-max-bytes}") long maxBytes) {
        if(maxBytes<1||maxBytes>10*1024*1024) throw new IllegalArgumentException("UPLOAD_MAX_BYTES must be between 1 and 10485760");
        this.maxBytes=maxBytes;
    }
    public long maxBytes() { return maxBytes; }
    public Validated validate(MultipartFile file) {
        if(file.isEmpty()) throw new ApiException(400,"EMPTY_FILE","Choose a non-empty PDF or UTF-8 text file");
        if(file.getSize()>maxBytes) throw tooLarge();
        byte[] bytes;
        try(var input=file.getInputStream()) {
            bytes=input.readNBytes((int)maxBytes+1);
        } catch(java.io.IOException ex) { throw new ApiException(400,"UPLOAD_FAILED","Could not read the uploaded file"); }
        if(bytes.length>maxBytes) throw tooLarge();
        String original=file.getOriginalFilename()==null?"":file.getOriginalFilename();
        String lower=original.toLowerCase(Locale.ROOT);
        String mime=file.getContentType()==null?"":file.getContentType().split(";",2)[0].strip().toLowerCase(Locale.ROOT);
        if(lower.endsWith(".pdf")&&mime.equals("application/pdf")) {
            String header=new String(bytes,0,Math.min(8,bytes.length),StandardCharsets.US_ASCII);
            String trailer=new String(bytes,Math.max(0,bytes.length-1024),Math.min(bytes.length,1024),StandardCharsets.ISO_8859_1);
            if(!header.matches("%PDF-[12]\\.[0-9]")||!trailer.contains("%%EOF")) throw invalid();
        } else if(lower.endsWith(".txt")&&mime.equals("text/plain")) {
            try {
                String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
                String start=text.replace("\uFEFF","").stripLeading().toLowerCase(Locale.ROOT);
                if(text.chars().anyMatch(c -> c<32&&c!='\n'&&c!='\r'&&c!='\t')
                    ||start.startsWith("mz")||start.startsWith("pk\u0003\u0004")||start.startsWith("%pdf-")||start.startsWith("#!")
                    ||start.startsWith("<!doctype")||start.startsWith("<html")||start.startsWith("<script")||start.startsWith("<?xml")) throw invalid();
            } catch(CharacterCodingException ex) { throw invalid(); }
        } else throw invalid();
        String filename=original.replaceAll("[^A-Za-z0-9._ ()-]","_").strip();
        if(filename.length()>180) filename=filename.substring(0,176)+(mime.equals("application/pdf")?".pdf":".txt");
        return new Validated(filename,mime,bytes);
    }
    private ApiException invalid() { return new ApiException(400,"INVALID_FILE_TYPE","Upload a PDF or plain UTF-8 TXT file with a matching content type"); }
    private ApiException tooLarge() { return new ApiException(413,"FILE_TOO_LARGE","The file exceeds the configured upload limit"); }
}
