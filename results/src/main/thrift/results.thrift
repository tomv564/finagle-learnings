#@namespace scala io.tomv.timing.results.thrift

struct TimingEvent {
  1: required i32 type
  2: required i64 timeStamp
  3: optional string chipNumber
  4: optional string category
}

struct Result {
	1: required i32 rank
	2: required i32 overallRank
	3: required string name
	4: required string category
	5: required string time
}
