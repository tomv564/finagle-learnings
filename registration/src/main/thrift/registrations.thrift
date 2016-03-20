#@namespace scala io.tomv.timing.registration.thrift

struct Registration {
  1: string chipNumber;
  2: string name;
  3: string category;
}

exception RegistrationException {
  1: string description;
}

service RegistrationService {
  void create(1: Registration registration)
    throws(1: RegistrationException ex)
  Registration get(1: string chipNumber)
  list<Registration> getAll()
}