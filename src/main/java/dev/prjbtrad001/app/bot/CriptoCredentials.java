package dev.prjbtrad001.app.bot;

public record CriptoCredentials (String apiKey, String secretKey) {

    public static final CriptoCredentials DEFAULT = new CriptoCredentials("SUA_API_KEY", "SUA_SECRET_KEY");

    public static CriptoCredentials of(String apiKey, String secretKey) {
        return new CriptoCredentials(apiKey, secretKey);
    }
}
