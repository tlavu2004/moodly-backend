// Executed inside the existing MongoDB container; no helper container is created.
const expectedName = process.env.MONGODB_REPLICA_SET;
let status;
try {
  status = rs.status();
} catch (error) {
  if (error.code !== 94 && error.codeName !== "NotYetInitialized") {
    throw error;
  }
}

if (status) {
  if (status.set !== expectedName) {
    throw new Error("Existing replica-set name does not match configuration");
  }
  print("Replica set " + expectedName + " is already initialized.");
} else {
  const result = rs.initiate({
    _id: expectedName,
    members: [{
      _id: 0,
      host: process.env.MONGODB_REPLICA_ADVERTISED_HOST + ":" + process.env.MONGODB_PORT
    }]
  });
  if (result.ok !== 1) {
    throw new Error("Replica-set initialization failed: " + JSON.stringify(result));
  }
  print("Replica set " + expectedName + " initialized.");
}
