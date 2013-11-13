package tutorial.storm.trident.example;

import backtype.storm.Config;
import backtype.storm.LocalCluster;
import backtype.storm.generated.StormTopology;
import backtype.storm.spout.SchemeAsMultiScheme;
import backtype.storm.tuple.Fields;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import storm.kafka.KafkaConfig;
import storm.kafka.StringScheme;
import storm.kafka.trident.TransactionalTridentKafkaSpout;
import storm.kafka.trident.TridentKafkaConfig;
import storm.trident.Stream;
import storm.trident.TridentTopology;
import tutorial.storm.trident.operations.*;

import java.io.IOException;

/**
 * This is a really simple example on how do joins between streams with Trident.
 *
 * @author Davide Palmisano (davide.palmisano@peerindex.com)
 */
public class JoinExample {

    public static StormTopology buildTopology(TransactionalTridentKafkaSpout spout)
            throws IOException {

        TridentTopology topology = new TridentTopology();

        /**
         * First, grab the tweets stream. We're going to use it in two different places
         * and then, we'll going to join them.
         *
         */
        Stream contents = topology
                .newStream("tweets", spout)
                .each(new Fields("str"), new ParseTweet(), new Fields("text", "content", "user"));

        /**
         * Now, let's select and project only hashtags for each tweet.
         * This stream is basically a list of couples (tweetId, hashtag).
         *
         */
        Stream hashtags = contents
                .each(new Fields("content"), new OnlyHashtags())
                .each(new Fields("content"), new TweetIdExtractor(), new Fields("tweetId"))
                .each(new Fields("content"), new GetContentName(), new Fields("hashtag"))
                .project(new Fields("hashtag", "tweetId"));
                //.each(new Fields("content", "tweetId"), new DebugFilter());

        /**
         * And let's do the same for urls, obtaining a stream of couples
         * like (tweetId, url).
         *
         */
        Stream urls = contents
                .each(new Fields("content"), new OnlyUrls())
                .each(new Fields("content"), new TweetIdExtractor(), new Fields("tweetId"))
                .each(new Fields("content"), new GetContentName(), new Fields("url"))
                .project(new Fields("url", "tweetId"));
                //.each(new Fields("content", "tweetId"), new DebugFilter());

        /**
         * Now is time to join on the tweetId to get a stream of triples (tweetId, hashtag, url).
         *
         */
        topology.join(hashtags, new Fields("tweetId"), urls, new Fields("tweetId"), new Fields("tweetId", "hashtag", "url"))
                .each(new Fields("tweetId", "hashtag", "url"), new DebugFilter());

        return topology.build();

    }

    public static void main(String[] args) throws Exception {
        Preconditions.checkArgument(args.length == 1, "Please specify the test kafka broker host:port");
        String testKafkaBrokerHost = args[0];

        TransactionalTridentKafkaSpout tweetSpout = tweetSpout(testKafkaBrokerHost);

        Config conf = new Config();

        LocalCluster cluster = new LocalCluster();
        cluster.submitTopology("hackaton", conf, buildTopology(tweetSpout));

        while(!Thread.currentThread().isInterrupted()){
            Thread.sleep(500);
        }
    }

    private static TransactionalTridentKafkaSpout tweetSpout(String testKafkaBrokerHost) {
        KafkaConfig.BrokerHosts hosts = TridentKafkaConfig.StaticHosts.fromHostString(ImmutableList.of(testKafkaBrokerHost), 1);
        TridentKafkaConfig config = new TridentKafkaConfig(hosts, "test");
        config.scheme = new SchemeAsMultiScheme(new StringScheme());
        return new TransactionalTridentKafkaSpout(config);
    }

}
