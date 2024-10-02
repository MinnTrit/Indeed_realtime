import java.util.List;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
    	int totalPage = 3;
    	boolean headlessOption = false;
    	String searchKeyword = "Data Science";
    	Scrapper myScrapper = new Scrapper(headlessOption, searchKeyword);
    	List<Map<String, String>> resultList = myScrapper.toScrape(totalPage);
    	Producer myProducer = new Producer();
    	
//    	Initialize the consumer for the thread
    	Thread consumerThread = new Thread(() -> {
    		Consumer myConsumer = new Consumer();
    		myConsumer.consumeMessage();
    	});
    	consumerThread.start();
    	
    	//Clean up the jobs after launching the jobs
    	myScrapper.endPlaywright();
    	resultList.stream().forEach(jobName -> myProducer.toSendMessage("name", jobName));
    	myProducer.closeProducer();
    }
}
