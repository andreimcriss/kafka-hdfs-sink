import kafka.serializer.StringDecoder
import org.apache.spark.streaming._
import org.apache.spark.streaming.kafka._
import org.apache.spark.SparkConf
import com.databricks.spark.csv._
import com.databricks.spark.avro._
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.sql.SQLContext

object KafkaHDFSSink{

  def main(args: Array[String]): Unit = {
   if (args.length != 5) {
      System.err.println(s"""
        |Usage: KafkaHDFSSink <brokers> <topics> <destination-url>
        |  <brokers> is a list of one or more Kafka brokers
        |  <topics> is a list of one or more kafka topics to consume from
        |  <destination-url> is the url prefix (eg:in hdfs) into which to save the fragments. Fragment names will be suffixed with the timestamp. The fragments are directories.(eg: hdfs:///temp/kafka_files/)  
        |  <offset_to_start_from> is the position from where the comsumer should start to receive data. Choose between: smallest and largest
		|  <output_format> is the file format to output the files to. Choose between: parquet, avro and text.
		""".stripMargin)
      System.exit(1)
    }
   
        //Create SparkContext
    val conf = new SparkConf()
      .setMaster("yarn-client")
      .setAppName("KafkaConsumer")
      .set("spark.executor.memory", "5g")
      .set("spark.rdd.compress","true")
      .set("spark.storage.memoryFraction", "1")
      .set("spark.streaming.unpersist", "true")
	  //.set("spark.driver.allowMultipleContexts", "true")

     val Array(brokers, topics, destinationUrl, offset, outputformat) = args


    val sparkConf = new SparkConf().setAppName("KafkaConsumer_"+topics)
    val sc = new SparkContext(sparkConf)
	val ssc = new StreamingContext(sc, Seconds(2))
	//SparkSQL
	val sqlContext = new SQLContext(sc)
	
	val topicsSet = topics.split(",").toSet
    val kafkaParams = Map[String, String]("metadata.broker.list" -> brokers, "auto.offset.reset" -> offset)

    val messages = KafkaUtils.createDirectStream[String, String, StringDecoder, StringDecoder](
      ssc, kafkaParams, topicsSet)

	  if(outputformat == "parquet") {
    	  messages.foreachRDD( rdd =>{
			  if(!rdd.partitions.isEmpty)
			  {	
		 		  val timestamp: Long = System.currentTimeMillis / 1000
				  val json_rdd =  sqlContext.jsonRDD(rdd.map(_._2))
				  val df = json_rdd.toDF()
				  df.write.parquet(destinationUrl+timestamp)
		 		  //rdd.map(_._2).saveAsTextFile(destinationUrl+timestamp)
			
		}
    })
}
	  if(outputformat == "avro") {
    	  messages.foreachRDD( rdd =>{
			  if(!rdd.partitions.isEmpty)
			  {	
		 		  val timestamp: Long = System.currentTimeMillis / 1000
				  val json_rdd =  sqlContext.jsonRDD(rdd.map(_._2))
				  val df = json_rdd.toDF()
				  df.write.avro(destinationUrl+timestamp)
		 		  //rdd.map(_._2).saveAsTextFile(destinationUrl+timestamp)
			
		}
    })
}
	  if(outputformat == "text") {
    	  messages.foreachRDD( rdd =>{
			  if(!rdd.partitions.isEmpty)
			  {	
		 		  val timestamp: Long = System.currentTimeMillis / 1000
				  rdd.map(_._2).saveAsTextFile(destinationUrl+timestamp)
			
		}
    })
}
	
    
    ssc.checkpoint(destinationUrl+"__checkpoint")

    ssc.start()
    ssc.awaitTermination()
  }

}
