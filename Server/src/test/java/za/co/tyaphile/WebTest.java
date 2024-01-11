package za.co.tyaphile;

import io.github.bonigarcia.wdm.WebDriverManager;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

public class WebTest {
    private ECommerceServer server;
    private WebDriver driver;
    private static final File dir = new File("screenshots");
    private int counter = 1;

    @Test
    void testOpenFirstPage() throws IOException {
        WebElement results = driver.findElement(By.id("results"));
        assertEquals("div", results.getTagName());

        WebElement table = driver.findElement(By.tagName("table"));
        List<WebElement> tableRowElements = table.findElements(By.tagName("tr"));
        tableRowElements.remove(0);
        tableRowElements.forEach(x -> assertEquals(5, x.findElements(By.tagName("td")).size()));
        assertEquals(10, tableRowElements.size());
        takeScreenShot();
        removeAllItems();
    }

    @Test
    void testDeleteAllItems() throws IOException {
        WebElement results = driver.findElement(By.tagName(("table")));
        results.findElements(By.tagName("tr")).forEach(x -> {
            List<WebElement> elements = x.findElements(By.tagName("td"));
            if (!elements.isEmpty()) {
                WebElement element = elements.get(3).findElement(By.tagName("input"));
                assertNotNull(element.getAttribute("value"));
                element.click();
                try {
                    takeScreenShot();
                } catch (IOException e) {
                    fail(e);
                }
            }
        });
        driver.findElement(By.id("delete")).click();
        WebElement element = driver.findElement(By.id("noItemsText"));
        assertEquals("There are currently no items/products available for purchase", element.getText());

        takeScreenShot();
    }

    @Test
    void testLogin() throws IOException {
        WebElement table = driver.findElement(By.tagName("table"));
        List<WebElement> products = table.findElements(By.tagName("tr")).stream().map(x -> {
            List<WebElement> elements = x.findElements(By.tagName("td"));
            if(elements.isEmpty()) return null;
            return elements.get(4);
        }).filter(Objects::nonNull).toList();
        products.get(0).findElement(By.tagName("input")).click();
        assertEquals("You need to be logged in to perform this action", driver.switchTo().alert().getText());
        driver.switchTo().alert().dismiss();
        driver.findElement(By.id("login")).click();
        assertEquals("http://localhost:5000/login.html", driver.getCurrentUrl());
        driver.findElement(By.id("name")).sendKeys("Thulani Tyaphile");
        driver.findElement(By.id("email")).sendKeys("tj.tyaphile@gmail.com");
        takeScreenShot();
        driver.findElement(By.id("login")).click();
        driver.findElement(By.tagName("a")).click();
        assertEquals("http://localhost:5000/index.html", driver.getCurrentUrl());
        removeAllItems();
    }

    @BeforeAll
    static void setupScreenShotFolder() {
        File[] files = dir.listFiles();
        if (dir.exists()) {
            for (File file : files) {
                file.delete();
            }
            dir.delete();
        }
        dir.mkdir();
    }

    @BeforeEach
    void setServer() {
        server = new ECommerceServer();
        server.start();

        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--no-sandbox");
        options.addArguments("--headless");
        options.addArguments("--disable-gpu");
        options.addArguments("--start-maximized");
        options.addArguments("--start-fullscreen");

        driver = new ChromeDriver();
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(1));

        driver.get("http://localhost:5000/");
    }

    @AfterEach
    void cleanUp() throws InterruptedException {
        driver.quit();
        server.stop();
        server = null;
        Thread.sleep(500);
    }

    private void removeAllItems() {
        driver.get("http://localhost:5000/");
        try {
            List<String> ids = driver.findElement(By.tagName("table")).findElements(By.tagName("tr")).stream().map(x -> {
                List<WebElement> elements = x.findElements(By.tagName("td"));
                if (!elements.isEmpty()) {
                    WebElement element = elements.get(3).findElement(By.tagName("input"));
                    return element.getAttribute("value");
                }
                return null;
            }).filter(Objects::nonNull).toList();
            Unirest.post("http://localhost:5000/remove-products")
                    .body(new ArrayList<>(ids))
                    .asJson();
        } catch (NoSuchElementException ignored) {}
    }

    private void takeScreenShot() throws IOException {
        File screenShotFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
        FileUtils.copyFile(screenShotFile, new File(dir.getAbsolutePath() + "\\screen_" + counter + ".png"));
        counter++;
    }
}