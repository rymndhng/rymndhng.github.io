import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer

object Calculator extends App {

  case class Record(ts: Long, item: String)
  case class MultiRecord(ts: Long, items: Seq[String])


  def javaIntersect(items:Seq[Record]*): Seq[MultiRecord] = {

    // initial state
    var accum = ArrayBuffer()
    var counters = Array(items.size)
    var ts = 0
    for (x <- 0 until items.size) { counters(x) = 0 }

    // stateful helper methods
    def produce(): MultiRecord = {
      val output_items = List()
      for (x <- 0 until items.size;
           count <- counters(x) if counters(x) < items(x).size) {
        output_items.append(items(x).item)
      }
      MultiRecord(ts, output_items)
    }

    def advanceCounters(): Int = {
      """ Advances the counter for the next item in the list with smallest time.
      Returns 1 if successful, Returns 0 if no more counters to advance"""
      min = Integer.MAX
      list_to_increase = None

      """ find the min value """
      for ( x <- 0 until items.size;
            count <- counters(x)+1;
            ts <- items(x)(count) if count < items(x).size) {
        min = Math.min(min, ts)
      }

      if (min == Integer.MAX) {
        return -1;
        // throw an exception to break control flow?
        // throw new RuntimeException("I'm done")
      }
      for (x <- 0 until items.size;
           count <- counters(x) + 1;
           if count < items(x).size;
           if x(count).ts == min) {
        counters(x) = counters(x) + 1
      }

      return 0;
    }

    do {
      // update some state markers...
      accum.append(produce)
    } while(advanceCounters() != -1)
    // get last iteration
    accum.append(produce)

    accum
  }

  def intersect(items:Seq[Record]*): Seq[MultiRecord] = {

    @tailrec
    def intersect0(accum: Seq[MultiRecord], items:Seq[Seq[Record]]): Seq[MultiRecord] = {
      if (items.filter(_.size > 1).isEmpty) return accum

      //get the timestamp of the next item to replace
      val start_ts = items.filter(_.size > 1).map(_.tail.head.ts).min
      val next_set = items.map { xs =>
        if (xs.size > 1 && xs.tail.head.ts == start_ts) xs.tail else xs
      }

      val entry = MultiRecord(start_ts, items.map(_.head).filter(_.item != "None").map(_.item))
      intersect0(accum :+ entry, next_set)
    }

    intersect0(Nil, items)
  }

  val cars = List(Record(200001, "None"), Record(200410,"Corolla"), Record(200905, "Civic"))
  val city = List(Record(200001, "Vancouver"), Record(200901, "Toronto"))
  val work = List(Record(200001, "None"), Record(200003, "Objects. Inc"), Record(200810, "Functional Corp"))

  println(intersect(cars,city,work))
  println(javaIntersect(cars,city,work))
}
