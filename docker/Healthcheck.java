import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Uses only the JRE; container health does not depend on curl/wget packages. */
public final class Healthcheck {
  public static void main(String[] args) {
    try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()) {
      var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:8080/actuator/health"))
          .timeout(Duration.ofSeconds(3)).GET().build();
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200 || !response.body().contains("\"status\":\"UP\"")) System.exit(1);
    } catch (Exception error) {
      System.exit(1);
    }
  }
}
