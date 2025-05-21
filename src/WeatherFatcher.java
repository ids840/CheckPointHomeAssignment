import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

import com.google.gson.*;

public class WeatherFetcher {
    private static final String API_KEY = "5abc828db62c2d79d0fd9f96e98059c6";
    private static final String WEATHER_URL = "http://api.openweathermap.org/data/2.5/weather?q=%s&appid=%s";
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Random random = new Random();

    static class City {
        String name;
        int rank;
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://weather-automation-checkpoint-task.westeurope.cloudapp.azure.com:3000/cities"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            System.err.println("Failed to fetch city list. HTTP status: " + response.statusCode());
            return;
        }

        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        City[] cities = new Gson().fromJson(root.getAsJsonArray("cities"), City[].class);

        for (City city : cities) {
            fetchWeatherWithRetry(city.name);
        }
    }

    private static void fetchWeatherWithRetry(String city) {
        int attempts = 0;
        int maxAttempts = 5;
        long backoff = 1000;

        while (attempts < maxAttempts) {
            try {
                Thread.sleep(500 + random.nextInt(1500));

                String encodedCity = URLEncoder.encode(city, StandardCharsets.UTF_8);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(String.format(WEATHER_URL, encodedCity, API_KEY)))
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    double tempK = json.getAsJsonObject("main").get("temp").getAsDouble();
                    double tempC = tempK - 273.15;
                    System.out.printf("%s : %.2f°C%n", city, tempC);
                    return;
                } else if (response.statusCode() == 429) {
                    Thread.sleep(backoff);
                    backoff *= 2;
                } else {
                    System.err.printf("Error fetching weather for %s: HTTP %d%n", city, response.statusCode());
                    return;
                }
            } catch (Exception e) {
                System.err.printf("Exception for %s: %s%n", city, e.getMessage());
            }
            attempts++;
        }
        System.err.printf("Failed to fetch weather for %s after %d attempts%n", city, attempts);
    }
}