#@namespace scala io.tomv.timing.result.thrift

struct TimingEvent {
  1: required i64 timeStamp
  2: required i32 type
  3: optional string chipNumber;
  4: optional string category;
}