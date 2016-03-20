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
  Registration create(1: string name, 2: string category)
    throws(1: RegistrationException ex)
  Registration get(1: string chipNumber)
  list<Registration> getAll()
}