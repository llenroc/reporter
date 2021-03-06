package io.opentraffic.reporter;

import java.nio.ByteBuffer;
import java.util.Map;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

/*
 * this is used as a histogram entry in a given tile
 */
public class Segment {
  
  public static long INVALID_SEGMENT_ID = 0x3fffffffffffL;
  public long id;         //main segment id
  public long min, max;   //epoch seconds
  public int duration;    //epoch seconds
  public int length;      //meters
  public int queue;       //meters
  public int count;       //how many
  public static final int SIZE = 8 + 8 + 8 + 4 + 4 + 4 + 4;
  public Long next_id;    //optional next
  
  public Segment(long id, Long next_id, double start, double end, int length, int queue) {
    this.id = id;
    this.next_id = next_id;
    this.min = (long)Math.floor(start);
    this.max = (long)Math.ceil(end);
    this.duration = (int)Math.round(end - start);
    this.length = length;
    this.queue = queue;
    this.count = 1;
  }
  
  public Segment(long id, long min, long max, int duration, int length, int queue, int count,  Long next_id) {
    this.id = id;
    this.next_id = next_id;
    this.min = min;
    this.max = max;
    this.duration = duration;
    this.length = length;
    this.queue = queue;
    this.count = 1;
  }
  
  public void combine(Segment s) {
    double a = this.count/(double)(this.count + s.count);
    double b = s.count/(double)(this.count + s.count);
    this.min = Math.min(this.min, s.min);
    this.max = Math.max(this.max, s.max);
    this.duration = (int)Math.round(this.duration * a + s.duration * b);
    this.length = (int)Math.round(this.length * a + s.length * b);
    this.queue = (int)Math.round(this.queue * a + s.queue * b);    
    this.count += s.count;
  }
  
  //first 3 bits are hierarchy level then 22 bits of tile id. the rest we want zero'd out
  public long getTileId() {    
    return (id >> 3) & 0x3FFFFF;
  }
  
  public long getTileLevel() {
    return id & 0x7;
  }
  
  public static String columnLayout() {
    return "segment_id,next_segment_id,duration,count,length,queue_length,minimum_timestamp,maximum_timestamp,source,vehicle_type";
  }
  
  public void appendToStringBuffer(StringBuffer buffer, String source) {
    buffer.append('\n');
    buffer.append(Long.toString(id)); buffer.append(',');
    if(next_id != null)
      buffer.append(next_id);
    buffer.append(',');
    buffer.append(Integer.toString(duration)); buffer.append(',');
    buffer.append(Integer.toString(count)); buffer.append(',');
    buffer.append(Integer.toString(length)); buffer.append(',');
    buffer.append(Integer.toString(queue)); buffer.append(',');
    buffer.append(Long.toString(min)); buffer.append(',');
    buffer.append(Long.toString(max)); buffer.append(',');
    buffer.append(source); buffer.append(',');
    //TODO: parse this in the formatting processor or get it as a program argument
    buffer.append("AUTO");
  }

  public static class Serder implements Serde<Segment> {
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) { }    
    @Override
    public void close() { }

    public Serializer<Segment> serializer() {
      return new Serializer<Segment>() {
        @Override
        public void configure(Map<String, ?> configs, boolean isKey) { }
        @Override
        public byte[] serialize(String topic, Segment s) {
          if(s == null)
            return null;
          ByteBuffer buffer = ByteBuffer.allocate(SIZE + (s.next_id == null ? 0 : 8));
          buffer.putLong(s.id);
          buffer.putLong(s.min);
          buffer.putLong(s.max);
          buffer.putInt(s.duration);
          buffer.putInt(s.length);
          buffer.putInt(s.queue);
          buffer.putInt(s.count);
          if(s.next_id != null)
            buffer.putLong(s.next_id);
          return buffer.array();
        }
        @Override
        public void close() { }        
      };
    }

    public Deserializer<Segment> deserializer() {
      return new Deserializer<Segment>() {
        @Override
        public void configure(Map<String, ?> configs, boolean isKey) { }
        @Override
        public Segment deserialize(String topic, byte[] bytes) {
          if(bytes == null)
            return null;
          ByteBuffer buffer = ByteBuffer.wrap(bytes);
          return new Segment(buffer.getLong(), buffer.getLong(), buffer.getLong(), 
              buffer.getInt(), buffer.getInt(), buffer.getInt(), buffer.getInt(),
              buffer.hasRemaining() ? buffer.getLong() : null);
        }
        @Override
        public void close() { }
      };
    }

  }

}
