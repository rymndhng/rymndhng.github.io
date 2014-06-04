package ray;

import scala.annotation.tailrec

object Calculator extends App {

  case class Record(ts: Long, item: String)
  case class MultiRecord(ts: Long, items: Seq[String])

  def javaIntersect(items:Seq[Record]*): Seq[MultiRecord] = {

    // initial state
    var accum: Seq[MultiRecord] = Seq()
    var next_ts = items.map(_.head.ts).min            // cheating to get initial state, mutating b/c
    val counters:Array[Int] = new Array(items.size)
    for (x <- 0 until items.size) { counters(x) = 0 }

    // stateful helper methods
    def produce(): MultiRecord = {
      var output_items:List[String] = List()
      for (x <- 0 until items.size) {
        if (counters(x) < items(x).size) {
          output_items = items(x)(counters(x)).item :: output_items
        }
      }

      MultiRecord(next_ts, output_items.filter(_ != "None")) // cheated here by using functional data structure
    }

    def advanceCounters(): Int = {
      // Advances the counter for the next item in the list with smallest time.
      // Returns 1 if successful, Returns 0 if no more counters to advance
      //
      // Also mutates the global next_ts because I'm lazy and don't want to do another loop
      var min = Long.MaxValue

      /// find the min value
      for { x <- 0 until items.size} {
        val next_count = counters(x) + 1;
        if (next_count < items(x).size) {
          min = Math.min(min, items(x)(next_count).ts)
        }
      }

      next_ts = min   // WHY ARE YOU MODIFYING A GLOBAL??? because we have to??

      if (min == Long.MaxValue) return -1
      // options - throw an exception to break control flow?
      // throw new RuntimeException("I'm done")

      for (x <- 0 until items.size) {
        val next_count = counters(x) + 1
        if (next_count < items(x).size && items(x)(next_count).ts == min) {
          counters(x) = next_count
        }
      }

      // if theres any incomplete, return 0 so we continue executing
      var completed = true;
      for (x <- 0 until items.size if counters(x)+1 < items(x).size && completed) {
        completed = false
      }

      if (completed) -1 else 0
    }

    do {
      // update some state markers...
      accum = accum :+ produce()
    } while(advanceCounters() != -1)
    // get last iteration
    accum = accum :+ produce()

    accum
  }

  def intersect(items:Seq[Record]*): Seq[MultiRecord] = {

    @tailrec
    def intersect0(accum: Seq[MultiRecord], start_ts: Long, itemList:Seq[Seq[Record]]): Seq[MultiRecord] = {

      if (start_ts == Long.MaxValue) return accum

      val entry = MultiRecord(start_ts, itemList.map(_.head.item).filter(_ != "None").reverse.toList)

      val next_start_ts = itemList.filter(_.size > 1).map(_.tail.head.ts) match {
        case Nil => Long.MaxValue
        case xs => xs.min
      }

      val next_set = itemList.map {
        case last :: Nil                                   => Seq(last)
        case head :: tail if tail.head.ts == next_start_ts => tail
        case items                                         => items
      }

      intersect0(accum :+ entry, next_start_ts, next_set)
    }

    intersect0(Nil, items.map(_.head.ts).min, items)
  }

  val cars = List(Record(200001, "None"), Record(200410,"Corolla"), Record(200905, "Civic"))
  val city = List(Record(200001, "Vancouver"), Record(200901, "Toronto"))
  val work = List(Record(200001, "None"), Record(200003, "Objects. Inc"), Record(200810, "Functional Corp"))

  println(intersect(cars,city,work))
  println(javaIntersect(cars,city,work))
}
