# Generated TypeScript README
This README will guide you through the process of using the generated JavaScript SDK package for the connector `prospace-connector`. It will also provide examples on how to use your generated SDK to call your Data Connect queries and mutations.

***NOTE:** This README is generated alongside the generated SDK. If you make changes to this file, they will be overwritten when the SDK is regenerated.*

# Table of Contents
- [**Overview**](#generated-javascript-readme)
- [**Accessing the connector**](#accessing-the-connector)
  - [*Connecting to the local Emulator*](#connecting-to-the-local-emulator)
- [**Queries**](#queries)
  - [*GetAppUsers*](#getappusers)
  - [*GetAppUserById*](#getappuserbyid)
  - [*GetSpaceListings*](#getspacelistings)
  - [*GetSpaceSubdivisions*](#getspacesubdivisions)
  - [*GetSpaceSubdivisionsBySpaceId*](#getspacesubdivisionsbyspaceid)
  - [*GetBookingRequests*](#getbookingrequests)
  - [*GetWhishTransactions*](#getwhishtransactions)
  - [*GetSuccessfulTransactionsInRange*](#getsuccessfultransactionsinrange)
  - [*GetAuditSecurityLogs*](#getauditsecuritylogs)
  - [*GetAvatarCampaigns*](#getavatarcampaigns)
  - [*GetAdminPricingStates*](#getadminpricingstates)
- [**Mutations**](#mutations)
  - [*UpsertAppUser*](#upsertappuser)
  - [*UpsertSpaceListing*](#upsertspacelisting)
  - [*UpsertSpaceSubdivision*](#upsertspacesubdivision)
  - [*DeleteSpaceSubdivision*](#deletespacesubdivision)
  - [*UpsertBookingRequest*](#upsertbookingrequest)
  - [*InsertWhishTransaction*](#insertwhishtransaction)
  - [*UpsertWhishTransaction*](#upsertwhishtransaction)
  - [*InsertAuditSecurityLog*](#insertauditsecuritylog)
  - [*UpsertAvatarCampaign*](#upsertavatarcampaign)
  - [*UpsertAdminPricingState*](#upsertadminpricingstate)
  - [*DeleteSpaceListing*](#deletespacelisting)
  - [*DeleteBookingRequest*](#deletebookingrequest)

# Accessing the connector
A connector is a collection of Queries and Mutations. One SDK is generated for each connector - this SDK is generated for the connector `prospace-connector`. You can find more information about connectors in the [Data Connect documentation](https://firebase.google.com/docs/data-connect#how-does).

You can use this generated SDK by importing from the package `@prohost/dataconnect-generated` as shown below. Both CommonJS and ESM imports are supported.

You can also follow the instructions from the [Data Connect documentation](https://firebase.google.com/docs/data-connect/web-sdk#set-client).

```typescript
import { getDataConnect } from 'firebase/data-connect';
import { connectorConfig } from '@prohost/dataconnect-generated';

const dataConnect = getDataConnect(connectorConfig);
```

## Connecting to the local Emulator
By default, the connector will connect to the production service.

To connect to the emulator, you can use the following code.
You can also follow the emulator instructions from the [Data Connect documentation](https://firebase.google.com/docs/data-connect/web-sdk#instrument-clients).

```typescript
import { connectDataConnectEmulator, getDataConnect } from 'firebase/data-connect';
import { connectorConfig } from '@prohost/dataconnect-generated';

const dataConnect = getDataConnect(connectorConfig);
connectDataConnectEmulator(dataConnect, 'localhost', 9399);
```

After it's initialized, you can call your Data Connect [queries](#queries) and [mutations](#mutations) from your generated SDK.

# Queries

There are two ways to execute a Data Connect Query using the generated Web SDK:
- Using a Query Reference function, which returns a `QueryRef`
  - The `QueryRef` can be used as an argument to `executeQuery()`, which will execute the Query and return a `QueryPromise`
- Using an action shortcut function, which returns a `QueryPromise`
  - Calling the action shortcut function will execute the Query and return a `QueryPromise`

The following is true for both the action shortcut function and the `QueryRef` function:
- The `QueryPromise` returned will resolve to the result of the Query once it has finished executing
- If the Query accepts arguments, both the action shortcut function and the `QueryRef` function accept a single argument: an object that contains all the required variables (and the optional variables) for the Query
- Both functions can be called with or without passing in a `DataConnect` instance as an argument. If no `DataConnect` argument is passed in, then the generated SDK will call `getDataConnect(connectorConfig)` behind the scenes for you.

Below are examples of how to use the `prospace-connector` connector's generated functions to execute each query. You can also follow the examples from the [Data Connect documentation](https://firebase.google.com/docs/data-connect/web-sdk#using-queries).

## GetAppUsers
You can execute the `GetAppUsers` query using the following action shortcut function, or by calling `executeQuery()` after calling the following `QueryRef` function, both of which are defined in [dataconnect-generated/index.d.ts](./index.d.ts):
```typescript
getAppUsers(options?: ExecuteQueryOptions): QueryPromise<GetAppUsersData, undefined>;

interface GetAppUsersRef {
  ...
  /* Allow users to create refs without passing in DataConnect */
  (): QueryRef<GetAppUsersData, undefined>;
}
export const getAppUsersRef: GetAppUsersRef;
```
You can also pass in a `DataConnect` instance to the action shortcut function or `QueryRef` function.
```typescript
getAppUsers(dc: DataConnect, options?: ExecuteQueryOptions): QueryPromise<GetAppUsersData, undefined>;

interface GetAppUsersRef {
  ...
  (dc: DataConnect): QueryRef<GetAppUsersData, undefined>;
}
export const getAppUsersRef: GetAppUsersRef;
```

If you need the name of the operation without creating a ref, you can retrieve the operation name by calling the `operationName` property on the getAppUsersRef:
```typescript
const name = getAppUsersRef.operationName;
console.log(name);
```

### Variables
The `GetAppUsers` query has no variables.
### Return Type
Recall that executing the `GetAppUsers` query returns a `QueryPromise` that resolves to an object with a `data` property.

The `data` property is an object of type `GetAppUsersData`, which is defined in [dataconnect-generated/index.d.ts](./index.d.ts). It has the following fields:
```typescript
export interface GetAppUsersData {
  appUsers: ({
    id: string;
    email: string;
    fullName: string;
    role: string;
    specialty: string;
    phone: string;
    affiliation: string;
    syndicateNumber: string;
    governorate: string;
    isVerified: boolean;
    subscriptionExpiryMillis?: Int64String | null;
  } & AppUser_Key)[];
}
```
### Using `GetAppUsers`'s action shortcut function

```typescript
import { getDataConnect } from 'firebase/data-connect';
import { connectorConfig, getAppUsers } from '@prohost/dataconnect-generated';


// Call the `getAppUsers()` function to execute the query.
// You can use the `await` keyword to wait for the promise to resolve.
const { data } = await getAppUsers();

// You can also pass in a `DataConnect` instance to the action shortcut function.
const dataConnect = getDataConnect(connectorConfig);
const { data } = await getAppUsers(dataConnect);

console.log(data.appUsers);

// Or, you can use the `Promise` API.
getAppUsers().then((response) => {
  const data = response.data;
  console.log(data.appUsers);
});
```

### Using `GetAppUsers`'s `QueryRef` function

```typescript
import { getDataConnect, executeQuery } from 'firebase/data-connect';
import { connectorConfig, getAppUsersRef } from '@prohost/dataconnect-generated';


// Call the `getAppUsersRef()` function to get a reference to the query.
const ref = getAppUsersRef();

// You can also pass in a `DataConnect` instance to the `QueryRef` function.
const dataConnect = getDataConnect(connectorConfig);
const ref = getAppUsersRef(dataConnect);

// Call `executeQuery()` on the reference to execute the query.
// You can use the `await` keyword to wait for the promise to resolve.
const { data } = await executeQuery(ref);

console.log(data.appUsers);

// Or, you can use the `Promise` API.
executeQuery(ref).then((response) => {
  const data = response.data;
  console.log(data.appUsers);
});
```

## GetAppUserById
You can execute the `GetAppUserById` query using the following action shortcut function, or by calling `executeQuery()` after calling the following `QueryRef` function, both of which are defined in [dataconnect-generated/index.d.ts](./index.d.ts):
```typescript
getAppUserById(vars: GetAppUserByIdVariables, options?: ExecuteQueryOptions): QueryPromise<GetAppUserByIdData, GetAppUserByIdVariables>;

interface GetAppUserByIdRef {
  ...
  /* Allow users to create refs without passing in DataConnect */
  (vars: GetAppUserByIdVariables): QueryRef<GetAppUserByIdData, GetAppUserByIdVariables>;
}
export const getAppUserByIdRef: GetAppUserByIdRef;
```
You can also pass in a `DataConnect` instance to the action shortcut function or `QueryRef` function.
```typescript
getAppUserById(dc: DataConnect, vars: GetAppUserByIdVariables, options?: ExecuteQueryOptions): QueryPromise<GetAppUserByIdData, GetAppUserByIdVariables>;

interface GetAppUserByIdRef {
  ...
  (dc: DataConnect, vars: GetAppUserByIdVariables): QueryRef<GetAppUserByIdData, GetAppUserByIdVariables>;
}
export const getAppUserByIdRef: GetAppUserByIdRef;
```

If you need the name of the operation without creating a ref, you can retrieve the operation name by calling the `operationName` property on the getAppUserByIdRef:
```typescript
const name = getAppUserByIdRef.operationName;
console.log(name);
```

### Variables
The `GetAppUserById` query requires an argument of type `GetAppUserByIdVariables`, which is defined in [dataconnect-generated/index.d.ts](./index.d.ts). It has the following fields:

```typescript
export interface GetAppUserByIdVariables {
  id: string;
}
```
### Return Type
Recall that executing the `GetAppUserById` query returns a `QueryPromise` that resolves to an object with a `data` property.

The `data` property is an object of type `GetAppUserByIdData`, which is defined in [dataconnect-generated/index.d.ts](./index.d.ts). It has the following fields:
```typescript
export interface GetAppUserByIdData {
  appUser?: {
    id: string;
    email: string;
    fullName: string;
    role: string;
    specialty: string;
    phone: string;
    affiliation: string;
    syndicateNumber: string;
    governorate: string;
    isVerified: boolean;
    subscriptionExpiryMillis?: Int64String | null;
  } & AppUser_Key;
}
```
### Using `GetAppUserById`'s action shortcut function

```typescript
import { getDataConnect } from 'firebase/data-connect';
import { connectorConfig, getAppUserById, GetAppUserByIdVariables } from '@prohost/dataconnect-generated';

// The `GetAppUserById` query requires an argument of type `GetAppUserByIdVariables`:
const getAppUserByIdVars: GetAppUserByIdVariables = {
  id: ..., 
};

// Call the `getAppUserById()` function to execute the query.
// You can use the `await` keyword to wait for the promise to resolve.
const { data } = await getAppUserById(getAppUserByIdVars);
// Variables can be defined inline as well.
const { data } = await getAppUserById({ id: ..., });

// You can also pass in a `DataConnect` instance to the action shortcut function.
const dataConnect = getDataConnect(connectorConfig);
const { data } = await getAppUserById(dataConnect, getAppUserByIdVars);

console.log(data.appUser);

// Or, you can use the `Promise` API.
getAppUserById(getAppUserByIdVars).then((response) => {
  const data = response.data;
  console.log(data.appUser);
});
```

### Using `GetAppUserById`'s `QueryRef` function

```typescript
import { getDataConnect, executeQuery } from 'firebase/data-connect';
import { connectorConfig, getAppUserByIdRef, GetAppUserByIdVariables } from '@prohost/dataconnect-generated';

// The `GetAppUserById` query requires an argument of type `GetAppUserByIdVariables`:
const getAppUserByIdVars: GetAppUserByIdVariables = {
  id: ..., 
};

// Call the `getAppUserByIdRef()` function to get a reference to the query.
const ref = getAppUserByIdRef(getAppUserByIdVars);
// Variables can be defined inline as well.
const ref = getAppUserByIdRef({ id: ..., });

// You can also pass in a `DataConnect` instance to the `QueryRef` function.
const dataConnect = getDataConnect(connectorConfig);
const ref = getAppUserByIdRef(dataConnect, getAppUserByIdVars);

// Call `executeQuery()` on the reference to execute the query.
// You can use the `await` keyword to wait for the promise to resolve.
const { data } = await executeQuery(ref);

console.log(data.appUser);

// Or, you can use the `Promise` API.
executeQuery(ref).then((response) => {
  const data = response.data;
  console.log(data.appUser);
});
```

## GetSpaceListings
You can execute the `GetSpaceListings` query using the following action shortcut function, or by calling `executeQuery()` after calling the following `QueryRef` function, both of which are defined in [dataconnect-generated/index.d.ts](./index.d.ts):
```typescript
getSpaceListings(options?: ExecuteQueryOptions): QueryPromise<GetSpaceListingsData, undefined>;

interface GetSpaceListingsRef {
  ...
  /* Allow users to create refs without passing in DataConnect */
  (): QueryRef<GetSpaceListingsData, undefined>;
}
export const getSpaceListingsRef: GetSpaceListingsRef;
```
You can also pass in a `DataConnect` instance to the action shortcut function or `QueryRef` function.
```typescript
getSpaceListings(dc: DataConnect, options?: ExecuteQueryOptions): QueryPromise<GetSpaceListingsData, undefined>;

interface GetSpaceListingsRef {
  ...
  (dc: DataConnect): QueryRef<GetSpaceListingsData, undefined>;
}
export const getSpaceListingsRef: GetSpaceListingsRef;
```

If you need the name of the operation without creating a ref, you can retrieve the operation name by calling the `operationName` property on the getSpaceListingsRef:
```typescript
const name = getSpaceListingsRef.operationName;
console.log(name);
```

### Variables
The `GetSpaceListings` query has no variables.
### Return Type
Recall that executing the `GetSpaceListings` query returns a `QueryPromise` that resolves to an object with a `data` property.

The `data` property is an object of type `GetSpaceListingsData`, which is defined in [dataconnect-generated/index.d.ts](./index.d.ts). It has the following fields:
```typescript
export interface GetSpaceListingsData {
  spaceListings: ({
    id: string;
    title: string;
    spaceType: string;
    governorate: string;
    district: string;
    streetAddress: string;
    floorInfo: string;
    lat: number;
    lng: number;
    isShared: boolean;
    complementarySpecialties: string[];
    residentPractitioners: string[];
    essentialFacilities: string[];
    equipmentJson: string;
    rentalFormulasJson: string;
    rulesJson: string;
    scheduleJson: string;
    ownerId: string;
    ownerName: string;
    ownerPhone: string;
    ownerEmail: string;
    isVerified: boolean;
    isActiveSubscription: boolean;
    subscriptionExpiryMillis: Int64String;
    imageUrls: string[];
    videoTourDurationSec: number;
    baseMonthlyRateUsd: number;
    avatarEngagementViews: number;
    avatarInquiryClicks: number;
    subdivisionsJson?: string | null;
  } & SpaceListing_Key)[];
}
```
### Using `GetSpaceListings`'s action shortcut function

```typescript
import { getDataConnect } from 'firebase/data-connect';
import { connectorConfig, getSpaceListings } from '@prohost/dataconnect-generated';


// Call the `getSpaceListings()` function to execute the query.
// You can use the `await` keyword to wait for the promise to resolve.
const { data } = await getSpaceListings();

// You can also pass in a `DataConnect` instance to the action shortcut function.
const dataConnect = getDataConnect(connectorConfig);
const { data } = await getSpaceListings(dataConnect);

console.log(data.spaceListings);

// Or, you can use the `Promise` API.
getSpaceListings().then((response) => {
  const data = response.data;
  console.log(data.spaceListings);
});
```

### Using `GetSpaceListings`'s `QueryRef` function

```typescript
import { getDataConnect, executeQuery } from 'firebase/data-connect';
import { connectorConfig, getSpaceListingsRef } from '@prohost/dataconnect-generated';


// Call the `getSpaceListingsRef()` function to get a reference to the query.
const ref = getSpaceListingsRef();

// You can also pass in a `DataConnect` instance to the `QueryRef` function.
const dataConnect = getDataConnect(connectorConfig);
const ref = getSpaceListingsRef(dataConnect);

// Call `executeQuery()` on the reference to execute the query.
// You can use the `await` keyword to wait for the promise to resolve.
const { data } = await executeQuery(ref);

console.log(data.spaceListings);

// Or, you can use the `Promise` API.
executeQuery(ref).then((response) => {
  const data = response.data;
  console.log(data.spaceListings);
});
```

## GetSpaceSubdivisions
You can execute the `GetSpaceSubdivisions` query using the following action shortcut function, or by calling `executeQuery()` after calling the following `QueryRef` function, both of which are defined in [dataconnect-generated/index.d.ts](./index.d.ts):
```typescript
getSpaceSubdivisions(options?: ExecuteQueryOptions): QueryPromise<GetSpaceSubdivisionsData, undefined>;

interface GetSpaceSubdivisionsRef {
  ...
  /* Allow users to create refs without passing in DataConnect */
  (): QueryRef<GetSpaceSubdivisionsData, undefined>;
}
export const getSpaceSubdivisionsRef: GetSpaceSubdivisionsRef;
```
You can also pass in a `DataConnect` instance to the action shortcut function or `QueryRef` function.
```typescript
getSpaceSubdivisions(dc: DataConnect, options?: ExecuteQueryOptions): QueryPromise<GetSpaceSubdivisionsData, undefined>;

interface GetSpaceSubdivisionsRef {
  ...
  (dc: DataConnect): QueryRef<GetSpaceSubdivisionsData, undefined>;
}
export const getSpaceSubdivisionsRef: GetSpaceSubdivisionsRef;
```

If you need the name of the operation without creating a ref, you can retrieve the operation name by calling the `operationName` property on the getSpaceSubdivisionsRef:
```typescript
const name = getSpaceSubdivisionsRef.operationName;
console.log(name);
```

### Variables
The `GetSpaceSubdivisions` query has no variables.
### Return Type
Recall that executing the `GetSpaceSubdivisions` query returns a `QueryPromise` that resolves to an object with a `data` property.

The `data` property is an object of type `GetSpaceSubdivisionsData`, which is defined in [dataconnect-generated/index.d.ts](./index.d.ts). It has the following fields:
```typescript
export interface GetSpaceSubdivisionsData {
  spaceSubdivisions: ({
    id: string;
    spaceId: string;
    name: string;
    type: string;
    amenities: string[];
    strategiesJson: string;
  } & SpaceSubdivision_Key)[];
}
```
### Using `GetSpaceSubdivisions`'s action shortcut function

```typescript
import { getDataConnect } from 'firebase/data-connect';
import { connectorConfig, getSpaceSubdivisions } from '@prohost/dataconnect-generated';


// Call the `getSpaceSubdivisions()` function to execute the query.
// You can use the `await` keyword to wait for the promise to resolve.
const { data } = await getSpaceSubdivisions();

// You can also pass in a `DataConnect` instance to the action shortcut function.
const dataConnect = getDataConnect(connectorConfig);
const { data } = await getSpaceSubdivisions(dataConnect);

console.log(data.spaceSubdivisions);

// Or, you can use the `Promise` API.
getSpaceSubdivisions().then((response) => {
  const data = response.data;
  console.log(data.spaceSubdivisions);
});
```

### Using `GetSpaceSubdivisions`'s `QueryRef` function

```typescript
import { getDataConnect, executeQuery } from 'firebase/data-connect';
import { connectorConfig, getSpaceSubdivisionsRef } from '@prohost/dataconnect-generated';


// Call the `getSpaceSubdivisionsRef()` function to get a reference to the query.
const ref = getSpaceSubdivisionsRef();

// You can also pass in a `DataConnect` instance to the `QueryRef` function.
const dataConnect = getDataConnect(connectorConfig);
const ref = getSpaceSubdivisionsRef(dataConnect);

// Call `executeQuery()` on the reference to execute the query.
// You can use the `await` keyword to wait for the promise to resolve.
const { data } = await executeQuery(ref);

console.log(data.spaceSubdivisions);

// Or, you can use the `Promise` API.
executeQuery(ref).then((response) => {
  const data = response.data;
  console.log(data.spaceSubdivisions);
});
```

## GetSpaceSubdivisionsBySpaceId
You can execute the `GetSpaceSubdivisionsBySpaceId` query using the following action shortcut function, or by calling `executeQuery()` after calling the following `QueryRef` function, both of which are defined in [dataconnect-generated/index.d.ts](./index.d.ts):
```typescript
getSpaceSubdivisionsBySpaceId(vars: GetSpaceSubdivisionsBySpaceIdVariables, options?: ExecuteQueryOptions): QueryPromise<GetSpaceSubdivisionsBySpaceIdData, GetSpaceSubdivisionsBySpaceIdVariables>;

interface GetSpaceSubdivisionsBySpaceIdRef {
  ...
  /* Allow users to create refs without passing in DataConnect */
  (vars: GetSpaceSubdivisionsBySpaceIdVariables): QueryRef<GetSpaceSubdivisionsBySpaceIdData, GetSpaceSubdivisionsBySpaceIdVariables>;
}
export const getSpaceSubdivisionsBySpaceIdRef: GetSpaceSubdivisionsBySpaceIdRef;
```
You can also pass in a `DataConnect` instance to the action shortcut function or `QueryRef` function.
```typescript
getSpaceSubdivisionsBySpaceId(dc: DataConnect, vars: GetSpaceSubdivisionsBySpaceIdVariables, options?: ExecuteQueryOptions): QueryPromise<GetSpaceSubdivisionsBySpaceIdData, GetSpaceSubdivisionsBySpaceIdVariables>;

interface GetSpaceSubdivisionsBySpaceIdRef {
  ...
  (dc: DataConnect, vars: GetSpaceSubdivisionsBySpaceIdVariables): QueryRef<GetSpaceSubdivisionsBySpaceIdData, GetSpaceSubdivisionsBySpaceIdVariables>;
}
export const getSpaceSubdivisionsBySpaceIdRef: GetSpaceSubdivisionsBySpaceIdRef;
```

If you need the name of the operation without creating a ref, you can retrieve the operation name by calling the `operationName` property on the getSpaceSubdivisionsBySpaceIdRef:
```typescript
const name = getSpaceSubdivisionsBySpaceIdRef.operationName;
console.log(name);
```

### Variables
The `GetSpaceSubdivisionsBySpaceId` query requires an argument of type `GetSpaceSubdivisionsBySpaceIdVariables`, which is defined in [dataconnect-generated/index.d.ts](./index.d.ts). It has the following fields:

```typescript
export interface GetSpaceSubdivisionsBySpaceIdVariables {
  spaceId: string;
}
```
### Return Type
Recall that executing the `GetSpaceSubdivisionsBySpaceId` query returns a `QueryPromise` that resolves to an object with a `data` property.

The `data` property is an object of type `GetSpaceSubdivisionsBySpaceIdData`, which is defined in [dataconnect-generated/index.d.ts](./index.d.ts). It has the following fields:
```typescript
export interface GetSpaceSubdivisionsBySpaceIdData {
  spaceSubdivisions: ({
    id: string;
    spaceId: string;
    name: string;
    type: string;
    amenities: string[];
    strategiesJson: string;
  } & SpaceSubdivision_Key)[];
}
```
### Using `GetSpaceSubdivisionsBySpaceId`'s action shortcut function

```typescript
import { getDataConnect } from 'firebase/data-connect';
import { connectorConfig, getSpaceSubdivisionsBySpaceId, GetSpaceSubdivisionsBySpaceIdVariables } from '@prohost/dataconnect-generated';

// The `GetSpaceSubdivisionsBySpaceId` query requires an argument of type `GetSpaceSubdivisionsBySpaceIdVariables`:
const getSpaceSubdivisionsBySpaceIdVars: GetSpaceSubdivisionsBySpaceIdVariables = {
  spaceId: ..., 
};

// Call the `getSpaceSubdivisionsBySpaceId()` function to execute the query.
// You can use the `await` keyword to wait for the promise to resolve.
const { data } = await getSpaceSubdivisionsBySpaceId(getSpaceSubdivisionsBySpaceIdVars);
// Variables can be defined inline as well.
const { data } = await getSpaceSubdivisionsBySpaceId({ spaceId: ..., });

// You can also pass in a `DataConnect` instance to the action shortcut function.
const dataConnect = getDataConnect(connectorConfig);
const { data } = await getSpaceSubdivisionsBySpaceId(dataConnect, getSpaceSubdivisionsBySpaceIdVars);

console.log(data.spaceSubdivisions);

// Or, you can use the `Promise` API.
getSpaceSubdivisionsBySpaceId(getSpaceSubdivisionsBySpaceIdVars).then((response) => {
  const data = response.data;
  console.log(data.spaceSubdivisions);
});
```

### Using `GetSpaceSubdivisionsBySpaceId`'s `QueryRef` function

```typescript
import { getDataConnect, executeQuery } from 'firebase/data-connect';
import { connectorConfig, getSpaceSubdivisionsBySpaceIdRef, GetSpaceSubdivisionsBySpaceIdVariables } from '@prohost/dataconnect-generated';

// The `GetSpaceSubdivisionsBySpaceId` query requires an argument of type `GetSpaceSubdivisionsBySpaceIdVariables`:
const getSpaceSubdivisionsBySpaceIdVars: GetSpaceSubdivisionsBySpaceIdVariables = {
  spaceId: ..., 
};

// Call the `getSpaceSubdivisionsBySpaceIdRef()` function to get a reference to the query.
const ref = getSpaceSubdivisionsBySpaceIdRef(getSpaceSubdivisionsBySpaceIdVars);
// Variables can be defined inline as well.
const ref = getSpaceSubdivisionsBySpaceIdRef({ spaceId: ..., });

// You can also pass in a `DataConnect` instance to the `QueryRef` function.
const dataConnect = getDataConnect(connectorConfig);
const ref = getSpaceSubdivisionsBySpaceIdRef(dataConnect, getSpaceSubdivisionsBySpaceIdVars);

// Call `executeQuery()` on the reference to execute the query.
// You can use the `await` keyword to wait for the promise to resolve.
const { data } = await executeQuery(ref);

console.log(data.spaceSubdivisions);

// Or, you can use the `Promise` API.
executeQuery(ref).then((response) => {
  const data = response.data;
  console.log(data.spaceSubdivisions);
});
```

## GetBookingRequests
You can execute the `GetBookingRequests` query using the following action shortcut function, or by calling `executeQuery()` after calling the following `QueryRef` function, both of which are defined in [dataconnect-generated/index.d.ts](./index.d.ts):
```typescript
getBookingRequests(options?: ExecuteQueryOptions): QueryPromise<GetBookingRequestsData, undefined>;

interface GetBookingRequestsRef {
  ...
  /* Allow users to create refs without passing in DataConnect */
  (): QueryRef<GetBookingRequestsData, undefined>;
}
export const getBookingRequestsRef: GetBookingRequestsRef;
```
You can also pass in a `DataConnect` instance to the action shortcut function or `QueryRef` function.
```typescript
getBookingRequests(dc: DataConnect, options?: ExecuteQueryOptions): QueryPromise<GetBookingRequestsData, undefined>;

interface GetBookingRequestsRef {
  ...
  (dc: DataConnect): QueryRef<GetBookingRequestsData, undefined>;
}
export const getBookingRequestsRef: GetBookingRequestsRef;
```

If you need the name of the operation without creating a ref, you can retrieve the operation name by calling the `operationName` property on the getBookingRequestsRef:
```typescript
const name = getBookingRequestsRef.operationName;
console.log(name);
```

### Variables
The `GetBookingRequests` query has no variables.
### Return Type
Recall that executing the `GetBookingRequests` query returns a `QueryPromise` that resolves to an object with a `data` property.

The `data` property is an object of type `GetBookingRequestsData`, which is defined in [dataconnect-generated/index.d.ts](./index.d.ts). It has the following fields:
```typescript
export interface GetBookingRequestsData {
  bookingRequests: ({
    id: string;
    spaceId: string;
    spaceTitle: string;
    spaceDistrict: string;
    governorate: string;
    ownerId: string;
    ownerName: string;
    ownerPhone: string;
    practitionerId: string;
    practitionerName: string;
    practitionerEmail: string;
    practitionerPhone: string;
    practitionerSpecialty: string;
    practitionerSyndicateNumber: string;
    formulaJson: string;
    startDate: string;
    endDate: string;
    selectedDays: string[];
    selectedStartHour: string;
    selectedEndHour: string;
    selectedShift: string;
    selectedDateTimeRange: string;
    durationMonths: number;
    totalAmountUsd: number;
    clinicalNotes: string;
    status: string;
    createdAt: Int64String;
    reviewedAt?: Int64String | null;
    rejectionReason?: string | null;
    isExternalPaymentSettled: boolean;
    subdivisionId?: string | null;
    subdivisionName?: string | null;
    selectedStrategy?: string | null;
  } & BookingRequest_Key)[];
}
```
### Using `GetBookingRequests`'s action shortcut function

```typescript
import { getDataConnect } from 'firebase/data-connect';
import { connectorConfig, getBookingRequests } from '@prohost/dataconnect-generated';


// Call the `getBookingRequests()` function to execute the query.
// You can use the `await` keyword to wait for the promise to resolve.
const { data } = await getBookingRequests();

// You can also pass in a `DataConnect` instance to the action shortcut function.
const dataConnect = getDataConnect(connectorConfig);
const { data } = await getBookingRequests(dataConnect);

console.log(data.bookingRequests);

// Or, you can use the `Promise` API.
getBookingRequests().then((response) => {
  const data = response.data;
  console.log(data.bookingRequests);
});
```

### Using `GetBookingRequests`'s `QueryRef` function

```typescript
import { getDataConnect, executeQuery } from 'firebase/data-connect';
import { connectorConfig, getBookingRequestsRef } from '@prohost/dataconnect-generated';


// Call the `getBookingRequestsRef()` function to get a reference to the query.
const ref = getBookingRequestsRef();

// You can also pass in a `DataConnect` instance to the `QueryRef` function.
const dataConnect = getDataConnect(connectorConfig);
const ref = getBookingRequestsRef(dataConnect);

// Call `executeQuery()` on the reference to execute the query.
// You can use the `await` keyword to wait for the promise to resolve.
const { data } = await executeQuery(ref);

console.log(data.bookingRequests);

// Or, you can use the `Promise` API.
executeQuery(ref).then((response) => {
  const data = response.data;
  console.log(data.bookingRequests);
});
```

## GetWhishTransactions
You can execute the `GetWhishTransactions` query using the following action shortcut function, or by calling `executeQuery()` after calling the following `QueryRef` function, both of which are defined in [dataconnect-generated/index.d.ts](./index.d.ts):
```typescript
getWhishTransactions(options?: ExecuteQueryOptions): QueryPromise<GetWhishTransactionsData, undefined>;

interface GetWhishTransactionsRef {
  ...
  /* Allow users to create refs without passing in DataConnect */
  (): QueryRef<GetWhishTransactionsData, undefined>;
}
export const getWhishTransactionsRef: GetWhishTransactionsRef;
```
You can also pass in a `DataConnect` instance to the action shortcut function or `QueryRef` function.
```typescript
getWhishTransactions(dc: DataConnect, options?: ExecuteQueryOptions): QueryPromise<GetWhishTransactionsData, undefined>;

interface GetWhishTransactionsRef {
  ...
  (dc: DataConnect): QueryRef<GetWhishTransactionsData, undefined>;
}
export const getWhishTransactionsRef: GetWhishTransactionsRef;
```

If you need the name of the operation without creating a ref, you can retrieve the operation name by calling the `operationName` property on the getWhishTransactionsRef:
```typescript
const name = getWhishTransactionsRef.operationName;
console.log(name);
```

### Variables
The `GetWhishTransactions` query has no variables.
### Return Type
Recall that executing the `GetWhishTransactions` query returns a `QueryPromise` that resolves to an object with a `data` property.

The `data` property is an object of type `GetWhishTransactionsData`, which is defined in [dataconnect-generated/index.d.ts](./index.d.ts). It has the following fields:
```typescript
export interface GetWhishTransactionsData {
  whishTransactions: ({
    id: string;
    orderId: string;
    amountUsd: number;
    currency: string;
    status: string;
    timestamp: Int64String;
    payerName: string;
    payerPhone: string;
    channelId: string;
    sourceEmail: string;
    signatureHash: string;
    spaceId: string;
    spaceTitle: string;
    daysGranted: number;
    userId: string;
  } & WhishTransaction_Key)[];
}
```
### Using `GetWhishTransactions`'s action shortcut function

```typescript
import { getDataConnect } from 'firebase/data-connect';
import { connectorConfig, getWhishTransactions } from '@prohost/dataconnect-generated';


// Call the `getWhishTransactions()` function to execute the query.
// You can use the `await` keyword to wait for the promise to resolve.
const { data } = await getWhishTransactions();

// You can also pass in a `DataConnect` instance to the action shortcut function.
const dataConnect = getDataConnect(connectorConfig);
const { data } = await getWhishTransactions(dataConnect);

console.log(data.whishTransactions);

// Or, you can use the `Promise` API.
getWhishTransactions().then((response) => {
  const data = response.data;
  console.log(data.whishTransactions);
});
```

### Using `GetWhishTransactions`'s `QueryRef` function

```typescript
import { getDataConnect, executeQuery } from 'firebase/data-connect';
import { connectorConfig, getWhishTransactionsRef } from '@prohost/dataconnect-generated';


// Call the `getWhishTransactionsRef()` function to get a reference to the query.
const ref = getWhishTransactionsRef();

// You can also pass in a `DataConnect` instance to the `QueryRef` function.
const dataConnect = getDataConnect(connectorConfig);
const ref = getWhishTransactionsRef(dataConnect);

// Call `executeQuery()` on the reference to execute the query.
// You can use the `await` keyword to wait for the promise to resolve.
const { data } = await executeQuery(ref);

console.log(data.whishTransactions);

// Or, you can use the `Promise` API.
executeQuery(ref).then((response) => {
  const data = response.data;
  console.log(data.whishTransactions);
});
```

## GetSuccessfulTransactionsInRange
You can execute the `GetSuccessfulTransactionsInRange` query using the following action shortcut function, or by calling `executeQuery()` after calling the following `QueryRef` function, both of which are defined in [dataconnect-generated/index.d.ts](./index.d.ts):
```typescript
getSuccessfulTransactionsInRange(vars: GetSuccessfulTransactionsInRangeVariables, options?: ExecuteQueryOptions): QueryPromise<GetSuccessfulTransactionsInRangeData, GetSuccessfulTransactionsInRangeVariables>;

interface GetSuccessfulTransactionsInRangeRef {
  ...
  /* Allow users to create refs without passing in DataConnect */
  (vars: GetSuccessfulTransactionsInRangeVariables): QueryRef<GetSuccessfulTransactionsInRangeData, GetSuccessfulTransactionsInRangeVariables>;
}
export const getSuccessfulTransactionsInRangeRef: GetSuccessfulTransactionsInRangeRef;
```
You can also pass in a `DataConnect` instance to the action shortcut function or `QueryRef` function.
```typescript
getSuccessfulTransactionsInRange(dc: DataConnect, vars: GetSuccessfulTransactionsInRangeVariables, options?: ExecuteQueryOptions): QueryPromise<GetSuccessfulTransactionsInRangeData, GetSuccessfulTransactionsInRangeVariables>;

interface GetSuccessfulTransactionsInRangeRef {
  ...
  (dc: DataConnect, vars: GetSuccessfulTransactionsInRangeVariables): QueryRef<GetSuccessfulTransactionsInRangeData, GetSuccessfulTransactionsInRangeVariables>;
}
export const getSuccessfulTransactionsInRangeRef: GetSuccessfulTransactionsInRangeRef;
```

If you need the name of the operation without creating a ref, you can retrieve the operation name by calling the `operationName` property on the getSuccessfulTransactionsInRangeRef:
```typescript
const name = getSuccessfulTransactionsInRangeRef.operationName;
console.log(name);
```

### Variables
The `GetSuccessfulTransactionsInRange` query requires an argument of type `GetSuccessfulTransactionsInRangeVariables`, which is defined in [dataconnect-generated/index.d.ts](./index.d.ts). It has the following fields:

```typescript
export interface GetSuccessfulTransactionsInRangeVariables {
  startTimestamp: Int64String;
  endTimestamp: Int64String;
}
```
### Return Type
Recall that executing the `GetSuccessfulTransactionsInRange` query returns a `QueryPromise` that resolves to an object with a `data` property.

The `data` property is an object of type `GetSuccessfulTransactionsInRangeData`, which is defined in [dataconnect-generated/index.d.ts](./index.d.ts). It has the following fields:
```typescript
export interface GetSuccessfulTransactionsInRangeData {
  whishTransactions: ({
    id: string;
    spaceId: string;
    spaceTitle: string;
    amountUsd: number;
    currency: string;
    timestamp: Int64String;
    userId: string;
  } & WhishTransaction_Key)[];
}
```
### Using `GetSuccessfulTransactionsInRange`'s action shortcut function

```typescript
import { getDataConnect } from 'firebase/data-connect';
import { connectorConfig, getSuccessfulTransactionsInRange, GetSuccessfulTransactionsInRangeVariables } from '@prohost/dataconnect-generated';

// The `GetSuccessfulTransactionsInRange` query requires an argument of type `GetSuccessfulTransactionsInRangeVariables`:
const getSuccessfulTransactionsInRangeVars: GetSuccessfulTransactionsInRangeVariables = {
  startTimestamp: ..., 
  endTimestamp: ..., 
};

// Call the `getSuccessfulTransactionsInRange()` function to execute the query.
// You can use the `await` keyword to wait for the promise to resolve.
const { data } = await getSuccessfulTransactionsInRange(getSuccessfulTransactionsInRangeVars);
// Variables can be defined inline as well.
const { data } = await getSuccessfulTransactionsInRange({ startTimestamp: ..., endTimestamp: ..., });

// You can also pass in a `DataConnect` instance to the action shortcut function.
const dataConnect = getDataConnect(connectorConfig);
const { data } = await getSuccessfulTransactionsInRange(dataConnect, getSuccessfulTransactionsInRangeVars);

console.log(data.whishTransactions);

// Or, you can use the `Promise` API.
getSuccessfulTransactionsInRange(getSuccessfulTransactionsInRangeVars).then((response) => {
  const data = response.data;
  console.log(data.whishTransactions);
});
```

### Using `GetSuccessfulTransactionsInRange`'s `QueryRef` function

```typescript
import { getDataConnect, executeQuery } from 'firebase/data-connect';
import { connectorConfig, getSuccessfulTransactionsInRangeRef, GetSuccessfulTransactionsInRangeVariables } from '@prohost/dataconnect-generated';

// The `GetSuccessfulTransactionsInRange` query requires an argument of type `GetSuccessfulTransactionsInRangeVariables`:
const getSuccessfulTransactionsInRangeVars: GetSuccessfulTransactionsInRangeVariables = {
  startTimestamp: ..., 
  endTimestamp: ..., 
};

// Call the `getSuccessfulTransactionsInRangeRef()` function to get a reference to the query.
const ref = getSuccessfulTransactionsInRangeRef(getSuccessfulTransactionsInRangeVars);
// Variables can be defined inline as well.
const ref = getSuccessfulTransactionsInRangeRef({ startTimestamp: ..., endTimestamp: ..., });

// You can also pass in a `DataConnect` instance to the `QueryRef` function.
const dataConnect = getDataConnect(connectorConfig);
const ref = getSuccessfulTransactionsInRangeRef(dataConnect, getSuccessfulTransactionsInRangeVars);

// Call `executeQuery()` on the reference to execute the query.
// You can use the `await` keyword to wait for the promise to resolve.
const { data } = await executeQuery(ref);

console.log(data.whishTransactions);

// Or, you can use the `Promise` API.
executeQuery(ref).then((response) => {
  const data = response.data;
  console.log(data.whishTransactions);
});
```

## GetAuditSecurityLogs
You can execute the `GetAuditSecurityLogs` query using the following action shortcut function, or by calling `executeQuery()` after calling the following `QueryRef` function, both of which are defined in [dataconnect-generated/index.d.ts](./index.d.ts):
```typescript
getAuditSecurityLogs(options?: ExecuteQueryOptions): QueryPromise<GetAuditSecurityLogsData, undefined>;

interface GetAuditSecurityLogsRef {
  ...
  /* Allow users to create refs without passing in DataConnect */
  (): QueryRef<GetAuditSecurityLogsData, undefined>;
}
export const getAuditSecurityLogsRef: GetAuditSecurityLogsRef;
```
You can also pass in a `DataConnect` instance to the action shortcut function or `QueryRef` function.
```typescript
getAuditSecurityLogs(dc: DataConnect, options?: ExecuteQueryOptions): QueryPromise<GetAuditSecurityLogsData, undefined>;

interface GetAuditSecurityLogsRef {
  ...
  (dc: DataConnect): QueryRef<GetAuditSecurityLogsData, undefined>;
}
export const getAuditSecurityLogsRef: GetAuditSecurityLogsRef;
```

If you need the name of the operation without creating a ref, you can retrieve the operation name by calling the `operationName` property on the getAuditSecurityLogsRef:
```typescript
const name = getAuditSecurityLogsRef.operationName;
console.log(name);
```

### Variables
The `GetAuditSecurityLogs` query has no variables.
### Return Type
Recall that executing the `GetAuditSecurityLogs` query returns a `QueryPromise` that resolves to an object with a `data` property.

The `data` property is an object of type `GetAuditSecurityLogsData`, which is defined in [dataconnect-generated/index.d.ts](./index.d.ts). It has the following fields:
```typescript
export interface GetAuditSecurityLogsData {
  auditSecurityLogs: ({
    id: string;
    timestamp: Int64String;
    actionType: string;
    details: string;
    actorEmail: string;
    severity: string;
    ipAddress: string;
  } & AuditSecurityLog_Key)[];
}
```
### Using `GetAuditSecurityLogs`'s action shortcut function

```typescript
import { getDataConnect } from 'firebase/data-connect';
import { connectorConfig, getAuditSecurityLogs } from '@prohost/dataconnect-generated';


// Call the `getAuditSecurityLogs()` function to execute the query.
// You can use the `await` keyword to wait for the promise to resolve.
const { data } = await getAuditSecurityLogs();

// You can also pass in a `DataConnect` instance to the action shortcut function.
const dataConnect = getDataConnect(connectorConfig);
const { data } = await getAuditSecurityLogs(dataConnect);

console.log(data.auditSecurityLogs);

// Or, you can use the `Promise` API.
getAuditSecurityLogs().then((response) => {
  const data = response.data;
  console.log(data.auditSecurityLogs);
});
```

### Using `GetAuditSecurityLogs`'s `QueryRef` function

```typescript
import { getDataConnect, executeQuery } from 'firebase/data-connect';
import { connectorConfig, getAuditSecurityLogsRef } from '@prohost/dataconnect-generated';


// Call the `getAuditSecurityLogsRef()` function to get a reference to the query.
const ref = getAuditSecurityLogsRef();

// You can also pass in a `DataConnect` instance to the `QueryRef` function.
const dataConnect = getDataConnect(connectorConfig);
const ref = getAuditSecurityLogsRef(dataConnect);

// Call `executeQuery()` on the reference to execute the query.
// You can use the `await` keyword to wait for the promise to resolve.
const { data } = await executeQuery(ref);

console.log(data.auditSecurityLogs);

// Or, you can use the `Promise` API.
executeQuery(ref).then((response) => {
  const data = response.data;
  console.log(data.auditSecurityLogs);
});
```

## GetAvatarCampaigns
You can execute the `GetAvatarCampaigns` query using the following action shortcut function, or by calling `executeQuery()` after calling the following `QueryRef` function, both of which are defined in [dataconnect-generated/index.d.ts](./index.d.ts):
```typescript
getAvatarCampaigns(options?: ExecuteQueryOptions): QueryPromise<GetAvatarCampaignsData, undefined>;

interface GetAvatarCampaignsRef {
  ...
  /* Allow users to create refs without passing in DataConnect */
  (): QueryRef<GetAvatarCampaignsData, undefined>;
}
export const getAvatarCampaignsRef: GetAvatarCampaignsRef;
```
You can also pass in a `DataConnect` instance to the action shortcut function or `QueryRef` function.
```typescript
getAvatarCampaigns(dc: DataConnect, options?: ExecuteQueryOptions): QueryPromise<GetAvatarCampaignsData, undefined>;

interface GetAvatarCampaignsRef {
  ...
  (dc: DataConnect): QueryRef<GetAvatarCampaignsData, undefined>;
}
export const getAvatarCampaignsRef: GetAvatarCampaignsRef;
```

If you need the name of the operation without creating a ref, you can retrieve the operation name by calling the `operationName` property on the getAvatarCampaignsRef:
```typescript
const name = getAvatarCampaignsRef.operationName;
console.log(name);
```

### Variables
The `GetAvatarCampaigns` query has no variables.
### Return Type
Recall that executing the `GetAvatarCampaigns` query returns a `QueryPromise` that resolves to an object with a `data` property.

The `data` property is an object of type `GetAvatarCampaignsData`, which is defined in [dataconnect-generated/index.d.ts](./index.d.ts). It has the following fields:
```typescript
export interface GetAvatarCampaignsData {
  avatarCampaigns: ({
    id: string;
    spaceId: string;
    spaceTitle: string;
    instagramHandle: string;
    totalReelViews: number;
    linkClicks: number;
    inquiriesGenerated: number;
    generatedCaption: string;
    storyOverlayTag: string;
    lastNudgeText: string;
  } & AvatarCampaign_Key)[];
}
```
### Using `GetAvatarCampaigns`'s action shortcut function

```typescript
import { getDataConnect } from 'firebase/data-connect';
import { connectorConfig, getAvatarCampaigns } from '@prohost/dataconnect-generated';


// Call the `getAvatarCampaigns()` function to execute the query.
// You can use the `await` keyword to wait for the promise to resolve.
const { data } = await getAvatarCampaigns();

// You can also pass in a `DataConnect` instance to the action shortcut function.
const dataConnect = getDataConnect(connectorConfig);
const { data } = await getAvatarCampaigns(dataConnect);

console.log(data.avatarCampaigns);

// Or, you can use the `Promise` API.
getAvatarCampaigns().then((response) => {
  const data = response.data;
  console.log(data.avatarCampaigns);
});
```

### Using `GetAvatarCampaigns`'s `QueryRef` function

```typescript
import { getDataConnect, executeQuery } from 'firebase/data-connect';
import { connectorConfig, getAvatarCampaignsRef } from '@prohost/dataconnect-generated';


// Call the `getAvatarCampaignsRef()` function to get a reference to the query.
const ref = getAvatarCampaignsRef();

// You can also pass in a `DataConnect` instance to the `QueryRef` function.
const dataConnect = getDataConnect(connectorConfig);
const ref = getAvatarCampaignsRef(dataConnect);

// Call `executeQuery()` on the reference to execute the query.
// You can use the `await` keyword to wait for the promise to resolve.
const { data } = await executeQuery(ref);

console.log(data.avatarCampaigns);

// Or, you can use the `Promise` API.
executeQuery(ref).then((response) => {
  const data = response.data;
  console.log(data.avatarCampaigns);
});
```

## GetAdminPricingStates
You can execute the `GetAdminPricingStates` query using the following action shortcut function, or by calling `executeQuery()` after calling the following `QueryRef` function, both of which are defined in [dataconnect-generated/index.d.ts](./index.d.ts):
```typescript
getAdminPricingStates(options?: ExecuteQueryOptions): QueryPromise<GetAdminPricingStatesData, undefined>;

interface GetAdminPricingStatesRef {
  ...
  /* Allow users to create refs without passing in DataConnect */
  (): QueryRef<GetAdminPricingStatesData, undefined>;
}
export const getAdminPricingStatesRef: GetAdminPricingStatesRef;
```
You can also pass in a `DataConnect` instance to the action shortcut function or `QueryRef` function.
```typescript
getAdminPricingStates(dc: DataConnect, options?: ExecuteQueryOptions): QueryPromise<GetAdminPricingStatesData, undefined>;

interface GetAdminPricingStatesRef {
  ...
  (dc: DataConnect): QueryRef<GetAdminPricingStatesData, undefined>;
}
export const getAdminPricingStatesRef: GetAdminPricingStatesRef;
```

If you need the name of the operation without creating a ref, you can retrieve the operation name by calling the `operationName` property on the getAdminPricingStatesRef:
```typescript
const name = getAdminPricingStatesRef.operationName;
console.log(name);
```

### Variables
The `GetAdminPricingStates` query has no variables.
### Return Type
Recall that executing the `GetAdminPricingStates` query returns a `QueryPromise` that resolves to an object with a `data` property.

The `data` property is an object of type `GetAdminPricingStatesData`, which is defined in [dataconnect-generated/index.d.ts](./index.d.ts). It has the following fields:
```typescript
export interface GetAdminPricingStatesData {
  adminPricingStates: ({
    id: string;
    monthlySubscriptionFeeUsd: number;
    baselineFeeUsd: number;
    presetOptions: number[];
    merchantChannelId: string;
    merchantSource: string;
    merchantSecretKeyMasked: string;
  } & AdminPricingState_Key)[];
}
```
### Using `GetAdminPricingStates`'s action shortcut function

```typescript
import { getDataConnect } from 'firebase/data-connect';
import { connectorConfig, getAdminPricingStates } from '@prohost/dataconnect-generated';


// Call the `getAdminPricingStates()` function to execute the query.
// You can use the `await` keyword to wait for the promise to resolve.
const { data } = await getAdminPricingStates();

// You can also pass in a `DataConnect` instance to the action shortcut function.
const dataConnect = getDataConnect(connectorConfig);
const { data } = await getAdminPricingStates(dataConnect);

console.log(data.adminPricingStates);

// Or, you can use the `Promise` API.
getAdminPricingStates().then((response) => {
  const data = response.data;
  console.log(data.adminPricingStates);
});
```

### Using `GetAdminPricingStates`'s `QueryRef` function

```typescript
import { getDataConnect, executeQuery } from 'firebase/data-connect';
import { connectorConfig, getAdminPricingStatesRef } from '@prohost/dataconnect-generated';


// Call the `getAdminPricingStatesRef()` function to get a reference to the query.
const ref = getAdminPricingStatesRef();

// You can also pass in a `DataConnect` instance to the `QueryRef` function.
const dataConnect = getDataConnect(connectorConfig);
const ref = getAdminPricingStatesRef(dataConnect);

// Call `executeQuery()` on the reference to execute the query.
// You can use the `await` keyword to wait for the promise to resolve.
const { data } = await executeQuery(ref);

console.log(data.adminPricingStates);

// Or, you can use the `Promise` API.
executeQuery(ref).then((response) => {
  const data = response.data;
  console.log(data.adminPricingStates);
});
```

# Mutations

There are two ways to execute a Data Connect Mutation using the generated Web SDK:
- Using a Mutation Reference function, which returns a `MutationRef`
  - The `MutationRef` can be used as an argument to `executeMutation()`, which will execute the Mutation and return a `MutationPromise`
- Using an action shortcut function, which returns a `MutationPromise`
  - Calling the action shortcut function will execute the Mutation and return a `MutationPromise`

The following is true for both the action shortcut function and the `MutationRef` function:
- The `MutationPromise` returned will resolve to the result of the Mutation once it has finished executing
- If the Mutation accepts arguments, both the action shortcut function and the `MutationRef` function accept a single argument: an object that contains all the required variables (and the optional variables) for the Mutation
- Both functions can be called with or without passing in a `DataConnect` instance as an argument. If no `DataConnect` argument is passed in, then the generated SDK will call `getDataConnect(connectorConfig)` behind the scenes for you.

Below are examples of how to use the `prospace-connector` connector's generated functions to execute each mutation. You can also follow the examples from the [Data Connect documentation](https://firebase.google.com/docs/data-connect/web-sdk#using-mutations).

## UpsertAppUser
You can execute the `UpsertAppUser` mutation using the following action shortcut function, or by calling `executeMutation()` after calling the following `MutationRef` function, both of which are defined in [dataconnect-generated/index.d.ts](./index.d.ts):
```typescript
upsertAppUser(vars: UpsertAppUserVariables): MutationPromise<UpsertAppUserData, UpsertAppUserVariables>;

interface UpsertAppUserRef {
  ...
  /* Allow users to create refs without passing in DataConnect */
  (vars: UpsertAppUserVariables): MutationRef<UpsertAppUserData, UpsertAppUserVariables>;
}
export const upsertAppUserRef: UpsertAppUserRef;
```
You can also pass in a `DataConnect` instance to the action shortcut function or `MutationRef` function.
```typescript
upsertAppUser(dc: DataConnect, vars: UpsertAppUserVariables): MutationPromise<UpsertAppUserData, UpsertAppUserVariables>;

interface UpsertAppUserRef {
  ...
  (dc: DataConnect, vars: UpsertAppUserVariables): MutationRef<UpsertAppUserData, UpsertAppUserVariables>;
}
export const upsertAppUserRef: UpsertAppUserRef;
```

If you need the name of the operation without creating a ref, you can retrieve the operation name by calling the `operationName` property on the upsertAppUserRef:
```typescript
const name = upsertAppUserRef.operationName;
console.log(name);
```

### Variables
The `UpsertAppUser` mutation requires an argument of type `UpsertAppUserVariables`, which is defined in [dataconnect-generated/index.d.ts](./index.d.ts). It has the following fields:

```typescript
export interface UpsertAppUserVariables {
  id: string;
  email: string;
  fullName: string;
  role: string;
  specialty: string;
  phone: string;
  affiliation: string;
  syndicateNumber: string;
  governorate: string;
  isVerified: boolean;
  verificationStatus: string;
  verificationTier: string;
  verificationNotes?: string | null;
  trustScore: number;
  subscriptionExpiryMillis?: Int64String | null;
  ownerPackageTier: string;
  ownerPackageExpiryMillis?: Int64String | null;
  paygListingsBoughtCount: number;
}
```
### Return Type
Recall that executing the `UpsertAppUser` mutation returns a `MutationPromise` that resolves to an object with a `data` property.

The `data` property is an object of type `UpsertAppUserData`, which is defined in [dataconnect-generated/index.d.ts](./index.d.ts). It has the following fields:
```typescript
export interface UpsertAppUserData {
  appUser_upsert: AppUser_Key;
}
```
### Using `UpsertAppUser`'s action shortcut function

```typescript
import { getDataConnect } from 'firebase/data-connect';
import { connectorConfig, upsertAppUser, UpsertAppUserVariables } from '@prohost/dataconnect-generated';

// The `UpsertAppUser` mutation requires an argument of type `UpsertAppUserVariables`:
const upsertAppUserVars: UpsertAppUserVariables = {
  id: ..., 
  email: ..., 
  fullName: ..., 
  role: ..., 
  specialty: ..., 
  phone: ..., 
  affiliation: ..., 
  syndicateNumber: ..., 
  governorate: ..., 
  isVerified: ..., 
  verificationStatus: ..., 
  verificationTier: ..., 
  verificationNotes: ..., // optional
  trustScore: ..., 
  subscriptionExpiryMillis: ..., // optional
  ownerPackageTier: ..., 
  ownerPackageExpiryMillis: ..., // optional
  paygListingsBoughtCount: ..., 
};

// Call the `upsertAppUser()` function to execute the mutation.
// You can use the `await` keyword to wait for the promise to resolve.
const { data } = await upsertAppUser(upsertAppUserVars);
// Variables can be defined inline as well.
const { data } = await upsertAppUser({ id: ..., email: ..., fullName: ..., role: ..., specialty: ..., phone: ..., affiliation: ..., syndicateNumber: ..., governorate: ..., isVerified: ..., verificationStatus: ..., verificationTier: ..., verificationNotes: ..., trustScore: ..., subscriptionExpiryMillis: ..., ownerPackageTier: ..., ownerPackageExpiryMillis: ..., paygListingsBoughtCount: ..., });

// You can also pass in a `DataConnect` instance to the action shortcut function.
const dataConnect = getDataConnect(connectorConfig);
const { data } = await upsertAppUser(dataConnect, upsertAppUserVars);

console.log(data.appUser_upsert);

// Or, you can use the `Promise` API.
upsertAppUser(upsertAppUserVars).then((response) => {
  const data = response.data;
  console.log(data.appUser_upsert);
});
```

### Using `UpsertAppUser`'s `MutationRef` function

```typescript
import { getDataConnect, executeMutation } from 'firebase/data-connect';
import { connectorConfig, upsertAppUserRef, UpsertAppUserVariables } from '@prohost/dataconnect-generated';

// The `UpsertAppUser` mutation requires an argument of type `UpsertAppUserVariables`:
const upsertAppUserVars: UpsertAppUserVariables = {
  id: ..., 
  email: ..., 
  fullName: ..., 
  role: ..., 
  specialty: ..., 
  phone: ..., 
  affiliation: ..., 
  syndicateNumber: ..., 
  governorate: ..., 
  isVerified: ..., 
  verificationStatus: ..., 
  verificationTier: ..., 
  verificationNotes: ..., // optional
  trustScore: ..., 
  subscriptionExpiryMillis: ..., // optional
  ownerPackageTier: ..., 
  ownerPackageExpiryMillis: ..., // optional
  paygListingsBoughtCount: ..., 
};

// Call the `upsertAppUserRef()` function to get a reference to the mutation.
const ref = upsertAppUserRef(upsertAppUserVars);
// Variables can be defined inline as well.
const ref = upsertAppUserRef({ id: ..., email: ..., fullName: ..., role: ..., specialty: ..., phone: ..., affiliation: ..., syndicateNumber: ..., governorate: ..., isVerified: ..., verificationStatus: ..., verificationTier: ..., verificationNotes: ..., trustScore: ..., subscriptionExpiryMillis: ..., ownerPackageTier: ..., ownerPackageExpiryMillis: ..., paygListingsBoughtCount: ..., });

// You can also pass in a `DataConnect` instance to the `MutationRef` function.
const dataConnect = getDataConnect(connectorConfig);
const ref = upsertAppUserRef(dataConnect, upsertAppUserVars);

// Call `executeMutation()` on the reference to execute the mutation.
// You can use the `await` keyword to wait for the promise to resolve.
const { data } = await executeMutation(ref);

console.log(data.appUser_upsert);

// Or, you can use the `Promise` API.
executeMutation(ref).then((response) => {
  const data = response.data;
  console.log(data.appUser_upsert);
});
```

## UpsertSpaceListing
You can execute the `UpsertSpaceListing` mutation using the following action shortcut function, or by calling `executeMutation()` after calling the following `MutationRef` function, both of which are defined in [dataconnect-generated/index.d.ts](./index.d.ts):
```typescript
upsertSpaceListing(vars: UpsertSpaceListingVariables): MutationPromise<UpsertSpaceListingData, UpsertSpaceListingVariables>;

interface UpsertSpaceListingRef {
  ...
  /* Allow users to create refs without passing in DataConnect */
  (vars: UpsertSpaceListingVariables): MutationRef<UpsertSpaceListingData, UpsertSpaceListingVariables>;
}
export const upsertSpaceListingRef: UpsertSpaceListingRef;
```
You can also pass in a `DataConnect` instance to the action shortcut function or `MutationRef` function.
```typescript
upsertSpaceListing(dc: DataConnect, vars: UpsertSpaceListingVariables): MutationPromise<UpsertSpaceListingData, UpsertSpaceListingVariables>;

interface UpsertSpaceListingRef {
  ...
  (dc: DataConnect, vars: UpsertSpaceListingVariables): MutationRef<UpsertSpaceListingData, UpsertSpaceListingVariables>;
}
export const upsertSpaceListingRef: UpsertSpaceListingRef;
```

If you need the name of the operation without creating a ref, you can retrieve the operation name by calling the `operationName` property on the upsertSpaceListingRef:
```typescript
const name = upsertSpaceListingRef.operationName;
console.log(name);
```

### Variables
The `UpsertSpaceListing` mutation requires an argument of type `UpsertSpaceListingVariables`, which is defined in [dataconnect-generated/index.d.ts](./index.d.ts). It has the following fields:

```typescript
export interface UpsertSpaceListingVariables {
  id: string;
  title: string;
  spaceType: string;
  governorate: string;
  district: string;
  streetAddress: string;
  floorInfo: string;
  lat: number;
  lng: number;
  isShared: boolean;
  complementarySpecialties: string[];
  residentPractitioners: string[];
  essentialFacilities: string[];
  equipmentJson: string;
  rentalFormulasJson: string;
  rulesJson: string;
  scheduleJson: string;
  ownerId: string;
  ownerName: string;
  ownerPhone: string;
  ownerEmail: string;
  isVerified: boolean;
  isActiveSubscription: boolean;
  subscriptionExpiryMillis: Int64String;
  imageUrls: string[];
  videoTourDurationSec: number;
  baseMonthlyRateUsd: number;
  avatarEngagementViews: number;
  avatarInquiryClicks: number;
  subdivisionsJson?: string | null;
}
```
### Return Type
Recall that executing the `UpsertSpaceListing` mutation returns a `MutationPromise` that resolves to an object with a `data` property.

The `data` property is an object of type `UpsertSpaceListingData`, which is defined in [dataconnect-generated/index.d.ts](./index.d.ts). It has the following fields:
```typescript
export interface UpsertSpaceListingData {
  spaceListing_upsert: SpaceListing_Key;
}
```
### Using `UpsertSpaceListing`'s action shortcut function

```typescript
import { getDataConnect } from 'firebase/data-connect';
import { connectorConfig, upsertSpaceListing, UpsertSpaceListingVariables } from '@prohost/dataconnect-generated';

// The `UpsertSpaceListing` mutation requires an argument of type `UpsertSpaceListingVariables`:
const upsertSpaceListingVars: UpsertSpaceListingVariables = {
  id: ..., 
  title: ..., 
  spaceType: ..., 
  governorate: ..., 
  district: ..., 
  streetAddress: ..., 
  floorInfo: ..., 
  lat: ..., 
  lng: ..., 
  isShared: ..., 
  complementarySpecialties: ..., 
  residentPractitioners: ..., 
  essentialFacilities: ..., 
  equipmentJson: ..., 
  rentalFormulasJson: ..., 
  rulesJson: ..., 
  scheduleJson: ..., 
  ownerId: ..., 
  ownerName: ..., 
  ownerPhone: ..., 
  ownerEmail: ..., 
  isVerified: ..., 
  isActiveSubscription: ..., 
  subscriptionExpiryMillis: ..., 
  imageUrls: ..., 
  videoTourDurationSec: ..., 
  baseMonthlyRateUsd: ..., 
  avatarEngagementViews: ..., 
  avatarInquiryClicks: ..., 
  subdivisionsJson: ..., // optional
};

// Call the `upsertSpaceListing()` function to execute the mutation.
// You can use the `await` keyword to wait for the promise to resolve.
const { data } = await upsertSpaceListing(upsertSpaceListingVars);
// Variables can be defined inline as well.
const { data } = await upsertSpaceListing({ id: ..., title: ..., spaceType: ..., governorate: ..., district: ..., streetAddress: ..., floorInfo: ..., lat: ..., lng: ..., isShared: ..., complementarySpecialties: ..., residentPractitioners: ..., essentialFacilities: ..., equipmentJson: ..., rentalFormulasJson: ..., rulesJson: ..., scheduleJson: ..., ownerId: ..., ownerName: ..., ownerPhone: ..., ownerEmail: ..., isVerified: ..., isActiveSubscription: ..., subscriptionExpiryMillis: ..., imageUrls: ..., videoTourDurationSec: ..., baseMonthlyRateUsd: ..., avatarEngagementViews: ..., avatarInquiryClicks: ..., subdivisionsJson: ..., });

// You can also pass in a `DataConnect` instance to the action shortcut function.
const dataConnect = getDataConnect(connectorConfig);
const { data } = await upsertSpaceListing(dataConnect, upsertSpaceListingVars);

console.log(data.spaceListing_upsert);

// Or, you can use the `Promise` API.
upsertSpaceListing(upsertSpaceListingVars).then((response) => {
  const data = response.data;
  console.log(data.spaceListing_upsert);
});
```

### Using `UpsertSpaceListing`'s `MutationRef` function

```typescript
import { getDataConnect, executeMutation } from 'firebase/data-connect';
import { connectorConfig, upsertSpaceListingRef, UpsertSpaceListingVariables } from '@prohost/dataconnect-generated';

// The `UpsertSpaceListing` mutation requires an argument of type `UpsertSpaceListingVariables`:
const upsertSpaceListingVars: UpsertSpaceListingVariables = {
  id: ..., 
  title: ..., 
  spaceType: ..., 
  governorate: ..., 
  district: ..., 
  streetAddress: ..., 
  floorInfo: ..., 
  lat: ..., 
  lng: ..., 
  isShared: ..., 
  complementarySpecialties: ..., 
  residentPractitioners: ..., 
  essentialFacilities: ..., 
  equipmentJson: ..., 
  rentalFormulasJson: ..., 
  rulesJson: ..., 
  scheduleJson: ..., 
  ownerId: ..., 
  ownerName: ..., 
  ownerPhone: ..., 
  ownerEmail: ..., 
  isVerified: ..., 
  isActiveSubscription: ..., 
  subscriptionExpiryMillis: ..., 
  imageUrls: ..., 
  videoTourDurationSec: ..., 
  baseMonthlyRateUsd: ..., 
  avatarEngagementViews: ..., 
  avatarInquiryClicks: ..., 
  subdivisionsJson: ..., // optional
};

// Call the `upsertSpaceListingRef()` function to get a reference to the mutation.
const ref = upsertSpaceListingRef(upsertSpaceListingVars);
// Variables can be defined inline as well.
const ref = upsertSpaceListingRef({ id: ..., title: ..., spaceType: ..., governorate: ..., district: ..., streetAddress: ..., floorInfo: ..., lat: ..., lng: ..., isShared: ..., complementarySpecialties: ..., residentPractitioners: ..., essentialFacilities: ..., equipmentJson: ..., rentalFormulasJson: ..., rulesJson: ..., scheduleJson: ..., ownerId: ..., ownerName: ..., ownerPhone: ..., ownerEmail: ..., isVerified: ..., isActiveSubscription: ..., subscriptionExpiryMillis: ..., imageUrls: ..., videoTourDurationSec: ..., baseMonthlyRateUsd: ..., avatarEngagementViews: ..., avatarInquiryClicks: ..., subdivisionsJson: ..., });

// You can also pass in a `DataConnect` instance to the `MutationRef` function.
const dataConnect = getDataConnect(connectorConfig);
const ref = upsertSpaceListingRef(dataConnect, upsertSpaceListingVars);

// Call `executeMutation()` on the reference to execute the mutation.
// You can use the `await` keyword to wait for the promise to resolve.
const { data } = await executeMutation(ref);

console.log(data.spaceListing_upsert);

// Or, you can use the `Promise` API.
executeMutation(ref).then((response) => {
  const data = response.data;
  console.log(data.spaceListing_upsert);
});
```

## UpsertSpaceSubdivision
You can execute the `UpsertSpaceSubdivision` mutation using the following action shortcut function, or by calling `executeMutation()` after calling the following `MutationRef` function, both of which are defined in [dataconnect-generated/index.d.ts](./index.d.ts):
```typescript
upsertSpaceSubdivision(vars: UpsertSpaceSubdivisionVariables): MutationPromise<UpsertSpaceSubdivisionData, UpsertSpaceSubdivisionVariables>;

interface UpsertSpaceSubdivisionRef {
  ...
  /* Allow users to create refs without passing in DataConnect */
  (vars: UpsertSpaceSubdivisionVariables): MutationRef<UpsertSpaceSubdivisionData, UpsertSpaceSubdivisionVariables>;
}
export const upsertSpaceSubdivisionRef: UpsertSpaceSubdivisionRef;
```
You can also pass in a `DataConnect` instance to the action shortcut function or `MutationRef` function.
```typescript
upsertSpaceSubdivision(dc: DataConnect, vars: UpsertSpaceSubdivisionVariables): MutationPromise<UpsertSpaceSubdivisionData, UpsertSpaceSubdivisionVariables>;

interface UpsertSpaceSubdivisionRef {
  ...
  (dc: DataConnect, vars: UpsertSpaceSubdivisionVariables): MutationRef<UpsertSpaceSubdivisionData, UpsertSpaceSubdivisionVariables>;
}
export const upsertSpaceSubdivisionRef: UpsertSpaceSubdivisionRef;
```

If you need the name of the operation without creating a ref, you can retrieve the operation name by calling the `operationName` property on the upsertSpaceSubdivisionRef:
```typescript
const name = upsertSpaceSubdivisionRef.operationName;
console.log(name);
```

### Variables
The `UpsertSpaceSubdivision` mutation requires an argument of type `UpsertSpaceSubdivisionVariables`, which is defined in [dataconnect-generated/index.d.ts](./index.d.ts). It has the following fields:

```typescript
export interface UpsertSpaceSubdivisionVariables {
  id: string;
  spaceId: string;
  name: string;
  type: string;
  amenities: string[];
  strategiesJson: string;
}
```
### Return Type
Recall that executing the `UpsertSpaceSubdivision` mutation returns a `MutationPromise` that resolves to an object with a `data` property.

The `data` property is an object of type `UpsertSpaceSubdivisionData`, which is defined in [dataconnect-generated/index.d.ts](./index.d.ts). It has the following fields:
```typescript
export interface UpsertSpaceSubdivisionData {
  spaceSubdivision_upsert: SpaceSubdivision_Key;
}
```
### Using `UpsertSpaceSubdivision`'s action shortcut function

```typescript
import { getDataConnect } from 'firebase/data-connect';
import { connectorConfig, upsertSpaceSubdivision, UpsertSpaceSubdivisionVariables } from '@prohost/dataconnect-generated';

// The `UpsertSpaceSubdivision` mutation requires an argument of type `UpsertSpaceSubdivisionVariables`:
const upsertSpaceSubdivisionVars: UpsertSpaceSubdivisionVariables = {
  id: ..., 
  spaceId: ..., 
  name: ..., 
  type: ..., 
  amenities: ..., 
  strategiesJson: ..., 
};

// Call the `upsertSpaceSubdivision()` function to execute the mutation.
// You can use the `await` keyword to wait for the promise to resolve.
const { data } = await upsertSpaceSubdivision(upsertSpaceSubdivisionVars);
// Variables can be defined inline as well.
const { data } = await upsertSpaceSubdivision({ id: ..., spaceId: ..., name: ..., type: ..., amenities: ..., strategiesJson: ..., });

// You can also pass in a `DataConnect` instance to the action shortcut function.
const dataConnect = getDataConnect(connectorConfig);
const { data } = await upsertSpaceSubdivision(dataConnect, upsertSpaceSubdivisionVars);

console.log(data.spaceSubdivision_upsert);

// Or, you can use the `Promise` API.
upsertSpaceSubdivision(upsertSpaceSubdivisionVars).then((response) => {
  const data = response.data;
  console.log(data.spaceSubdivision_upsert);
});
```

### Using `UpsertSpaceSubdivision`'s `MutationRef` function

```typescript
import { getDataConnect, executeMutation } from 'firebase/data-connect';
import { connectorConfig, upsertSpaceSubdivisionRef, UpsertSpaceSubdivisionVariables } from '@prohost/dataconnect-generated';

// The `UpsertSpaceSubdivision` mutation requires an argument of type `UpsertSpaceSubdivisionVariables`:
const upsertSpaceSubdivisionVars: UpsertSpaceSubdivisionVariables = {
  id: ..., 
  spaceId: ..., 
  name: ..., 
  type: ..., 
  amenities: ..., 
  strategiesJson: ..., 
};

// Call the `upsertSpaceSubdivisionRef()` function to get a reference to the mutation.
const ref = upsertSpaceSubdivisionRef(upsertSpaceSubdivisionVars);
// Variables can be defined inline as well.
const ref = upsertSpaceSubdivisionRef({ id: ..., spaceId: ..., name: ..., type: ..., amenities: ..., strategiesJson: ..., });

// You can also pass in a `DataConnect` instance to the `MutationRef` function.
const dataConnect = getDataConnect(connectorConfig);
const ref = upsertSpaceSubdivisionRef(dataConnect, upsertSpaceSubdivisionVars);

// Call `executeMutation()` on the reference to execute the mutation.
// You can use the `await` keyword to wait for the promise to resolve.
const { data } = await executeMutation(ref);

console.log(data.spaceSubdivision_upsert);

// Or, you can use the `Promise` API.
executeMutation(ref).then((response) => {
  const data = response.data;
  console.log(data.spaceSubdivision_upsert);
});
```

## DeleteSpaceSubdivision
You can execute the `DeleteSpaceSubdivision` mutation using the following action shortcut function, or by calling `executeMutation()` after calling the following `MutationRef` function, both of which are defined in [dataconnect-generated/index.d.ts](./index.d.ts):
```typescript
deleteSpaceSubdivision(vars: DeleteSpaceSubdivisionVariables): MutationPromise<DeleteSpaceSubdivisionData, DeleteSpaceSubdivisionVariables>;

interface DeleteSpaceSubdivisionRef {
  ...
  /* Allow users to create refs without passing in DataConnect */
  (vars: DeleteSpaceSubdivisionVariables): MutationRef<DeleteSpaceSubdivisionData, DeleteSpaceSubdivisionVariables>;
}
export const deleteSpaceSubdivisionRef: DeleteSpaceSubdivisionRef;
```
You can also pass in a `DataConnect` instance to the action shortcut function or `MutationRef` function.
```typescript
deleteSpaceSubdivision(dc: DataConnect, vars: DeleteSpaceSubdivisionVariables): MutationPromise<DeleteSpaceSubdivisionData, DeleteSpaceSubdivisionVariables>;

interface DeleteSpaceSubdivisionRef {
  ...
  (dc: DataConnect, vars: DeleteSpaceSubdivisionVariables): MutationRef<DeleteSpaceSubdivisionData, DeleteSpaceSubdivisionVariables>;
}
export const deleteSpaceSubdivisionRef: DeleteSpaceSubdivisionRef;
```

If you need the name of the operation without creating a ref, you can retrieve the operation name by calling the `operationName` property on the deleteSpaceSubdivisionRef:
```typescript
const name = deleteSpaceSubdivisionRef.operationName;
console.log(name);
```

### Variables
The `DeleteSpaceSubdivision` mutation requires an argument of type `DeleteSpaceSubdivisionVariables`, which is defined in [dataconnect-generated/index.d.ts](./index.d.ts). It has the following fields:

```typescript
export interface DeleteSpaceSubdivisionVariables {
  id: string;
}
```
### Return Type
Recall that executing the `DeleteSpaceSubdivision` mutation returns a `MutationPromise` that resolves to an object with a `data` property.

The `data` property is an object of type `DeleteSpaceSubdivisionData`, which is defined in [dataconnect-generated/index.d.ts](./index.d.ts). It has the following fields:
```typescript
export interface DeleteSpaceSubdivisionData {
  spaceSubdivision_delete?: SpaceSubdivision_Key | null;
}
```
### Using `DeleteSpaceSubdivision`'s action shortcut function

```typescript
import { getDataConnect } from 'firebase/data-connect';
import { connectorConfig, deleteSpaceSubdivision, DeleteSpaceSubdivisionVariables } from '@prohost/dataconnect-generated';

// The `DeleteSpaceSubdivision` mutation requires an argument of type `DeleteSpaceSubdivisionVariables`:
const deleteSpaceSubdivisionVars: DeleteSpaceSubdivisionVariables = {
  id: ..., 
};

// Call the `deleteSpaceSubdivision()` function to execute the mutation.
// You can use the `await` keyword to wait for the promise to resolve.
const { data } = await deleteSpaceSubdivision(deleteSpaceSubdivisionVars);
// Variables can be defined inline as well.
const { data } = await deleteSpaceSubdivision({ id: ..., });

// You can also pass in a `DataConnect` instance to the action shortcut function.
const dataConnect = getDataConnect(connectorConfig);
const { data } = await deleteSpaceSubdivision(dataConnect, deleteSpaceSubdivisionVars);

console.log(data.spaceSubdivision_delete);

// Or, you can use the `Promise` API.
deleteSpaceSubdivision(deleteSpaceSubdivisionVars).then((response) => {
  const data = response.data;
  console.log(data.spaceSubdivision_delete);
});
```

### Using `DeleteSpaceSubdivision`'s `MutationRef` function

```typescript
import { getDataConnect, executeMutation } from 'firebase/data-connect';
import { connectorConfig, deleteSpaceSubdivisionRef, DeleteSpaceSubdivisionVariables } from '@prohost/dataconnect-generated';

// The `DeleteSpaceSubdivision` mutation requires an argument of type `DeleteSpaceSubdivisionVariables`:
const deleteSpaceSubdivisionVars: DeleteSpaceSubdivisionVariables = {
  id: ..., 
};

// Call the `deleteSpaceSubdivisionRef()` function to get a reference to the mutation.
const ref = deleteSpaceSubdivisionRef(deleteSpaceSubdivisionVars);
// Variables can be defined inline as well.
const ref = deleteSpaceSubdivisionRef({ id: ..., });

// You can also pass in a `DataConnect` instance to the `MutationRef` function.
const dataConnect = getDataConnect(connectorConfig);
const ref = deleteSpaceSubdivisionRef(dataConnect, deleteSpaceSubdivisionVars);

// Call `executeMutation()` on the reference to execute the mutation.
// You can use the `await` keyword to wait for the promise to resolve.
const { data } = await executeMutation(ref);

console.log(data.spaceSubdivision_delete);

// Or, you can use the `Promise` API.
executeMutation(ref).then((response) => {
  const data = response.data;
  console.log(data.spaceSubdivision_delete);
});
```

## UpsertBookingRequest
You can execute the `UpsertBookingRequest` mutation using the following action shortcut function, or by calling `executeMutation()` after calling the following `MutationRef` function, both of which are defined in [dataconnect-generated/index.d.ts](./index.d.ts):
```typescript
upsertBookingRequest(vars: UpsertBookingRequestVariables): MutationPromise<UpsertBookingRequestData, UpsertBookingRequestVariables>;

interface UpsertBookingRequestRef {
  ...
  /* Allow users to create refs without passing in DataConnect */
  (vars: UpsertBookingRequestVariables): MutationRef<UpsertBookingRequestData, UpsertBookingRequestVariables>;
}
export const upsertBookingRequestRef: UpsertBookingRequestRef;
```
You can also pass in a `DataConnect` instance to the action shortcut function or `MutationRef` function.
```typescript
upsertBookingRequest(dc: DataConnect, vars: UpsertBookingRequestVariables): MutationPromise<UpsertBookingRequestData, UpsertBookingRequestVariables>;

interface UpsertBookingRequestRef {
  ...
  (dc: DataConnect, vars: UpsertBookingRequestVariables): MutationRef<UpsertBookingRequestData, UpsertBookingRequestVariables>;
}
export const upsertBookingRequestRef: UpsertBookingRequestRef;
```

If you need the name of the operation without creating a ref, you can retrieve the operation name by calling the `operationName` property on the upsertBookingRequestRef:
```typescript
const name = upsertBookingRequestRef.operationName;
console.log(name);
```

### Variables
The `UpsertBookingRequest` mutation requires an argument of type `UpsertBookingRequestVariables`, which is defined in [dataconnect-generated/index.d.ts](./index.d.ts). It has the following fields:

```typescript
export interface UpsertBookingRequestVariables {
  id: string;
  spaceId: string;
  spaceTitle: string;
  spaceDistrict: string;
  governorate: string;
  ownerId: string;
  ownerName: string;
  ownerPhone: string;
  practitionerId: string;
  practitionerName: string;
  practitionerEmail: string;
  practitionerPhone: string;
  practitionerSpecialty: string;
  practitionerSyndicateNumber: string;
  formulaJson: string;
  startDate: string;
  endDate: string;
  selectedDays: string[];
  selectedStartHour: string;
  selectedEndHour: string;
  selectedShift: string;
  selectedDateTimeRange: string;
  durationMonths: number;
  totalAmountUsd: number;
  clinicalNotes: string;
  status: string;
  createdAt: Int64String;
  reviewedAt?: Int64String | null;
  rejectionReason?: string | null;
  isExternalPaymentSettled: boolean;
  subdivisionId?: string | null;
  subdivisionName?: string | null;
  selectedStrategy?: string | null;
}
```
### Return Type
Recall that executing the `UpsertBookingRequest` mutation returns a `MutationPromise` that resolves to an object with a `data` property.

The `data` property is an object of type `UpsertBookingRequestData`, which is defined in [dataconnect-generated/index.d.ts](./index.d.ts). It has the following fields:
```typescript
export interface UpsertBookingRequestData {
  bookingRequest_upsert: BookingRequest_Key;
}
```
### Using `UpsertBookingRequest`'s action shortcut function

```typescript
import { getDataConnect } from 'firebase/data-connect';
import { connectorConfig, upsertBookingRequest, UpsertBookingRequestVariables } from '@prohost/dataconnect-generated';

// The `UpsertBookingRequest` mutation requires an argument of type `UpsertBookingRequestVariables`:
const upsertBookingRequestVars: UpsertBookingRequestVariables = {
  id: ..., 
  spaceId: ..., 
  spaceTitle: ..., 
  spaceDistrict: ..., 
  governorate: ..., 
  ownerId: ..., 
  ownerName: ..., 
  ownerPhone: ..., 
  practitionerId: ..., 
  practitionerName: ..., 
  practitionerEmail: ..., 
  practitionerPhone: ..., 
  practitionerSpecialty: ..., 
  practitionerSyndicateNumber: ..., 
  formulaJson: ..., 
  startDate: ..., 
  endDate: ..., 
  selectedDays: ..., 
  selectedStartHour: ..., 
  selectedEndHour: ..., 
  selectedShift: ..., 
  selectedDateTimeRange: ..., 
  durationMonths: ..., 
  totalAmountUsd: ..., 
  clinicalNotes: ..., 
  status: ..., 
  createdAt: ..., 
  reviewedAt: ..., // optional
  rejectionReason: ..., // optional
  isExternalPaymentSettled: ..., 
  subdivisionId: ..., // optional
  subdivisionName: ..., // optional
  selectedStrategy: ..., // optional
};

// Call the `upsertBookingRequest()` function to execute the mutation.
// You can use the `await` keyword to wait for the promise to resolve.
const { data } = await upsertBookingRequest(upsertBookingRequestVars);
// Variables can be defined inline as well.
const { data } = await upsertBookingRequest({ id: ..., spaceId: ..., spaceTitle: ..., spaceDistrict: ..., governorate: ..., ownerId: ..., ownerName: ..., ownerPhone: ..., practitionerId: ..., practitionerName: ..., practitionerEmail: ..., practitionerPhone: ..., practitionerSpecialty: ..., practitionerSyndicateNumber: ..., formulaJson: ..., startDate: ..., endDate: ..., selectedDays: ..., selectedStartHour: ..., selectedEndHour: ..., selectedShift: ..., selectedDateTimeRange: ..., durationMonths: ..., totalAmountUsd: ..., clinicalNotes: ..., status: ..., createdAt: ..., reviewedAt: ..., rejectionReason: ..., isExternalPaymentSettled: ..., subdivisionId: ..., subdivisionName: ..., selectedStrategy: ..., });

// You can also pass in a `DataConnect` instance to the action shortcut function.
const dataConnect = getDataConnect(connectorConfig);
const { data } = await upsertBookingRequest(dataConnect, upsertBookingRequestVars);

console.log(data.bookingRequest_upsert);

// Or, you can use the `Promise` API.
upsertBookingRequest(upsertBookingRequestVars).then((response) => {
  const data = response.data;
  console.log(data.bookingRequest_upsert);
});
```

### Using `UpsertBookingRequest`'s `MutationRef` function

```typescript
import { getDataConnect, executeMutation } from 'firebase/data-connect';
import { connectorConfig, upsertBookingRequestRef, UpsertBookingRequestVariables } from '@prohost/dataconnect-generated';

// The `UpsertBookingRequest` mutation requires an argument of type `UpsertBookingRequestVariables`:
const upsertBookingRequestVars: UpsertBookingRequestVariables = {
  id: ..., 
  spaceId: ..., 
  spaceTitle: ..., 
  spaceDistrict: ..., 
  governorate: ..., 
  ownerId: ..., 
  ownerName: ..., 
  ownerPhone: ..., 
  practitionerId: ..., 
  practitionerName: ..., 
  practitionerEmail: ..., 
  practitionerPhone: ..., 
  practitionerSpecialty: ..., 
  practitionerSyndicateNumber: ..., 
  formulaJson: ..., 
  startDate: ..., 
  endDate: ..., 
  selectedDays: ..., 
  selectedStartHour: ..., 
  selectedEndHour: ..., 
  selectedShift: ..., 
  selectedDateTimeRange: ..., 
  durationMonths: ..., 
  totalAmountUsd: ..., 
  clinicalNotes: ..., 
  status: ..., 
  createdAt: ..., 
  reviewedAt: ..., // optional
  rejectionReason: ..., // optional
  isExternalPaymentSettled: ..., 
  subdivisionId: ..., // optional
  subdivisionName: ..., // optional
  selectedStrategy: ..., // optional
};

// Call the `upsertBookingRequestRef()` function to get a reference to the mutation.
const ref = upsertBookingRequestRef(upsertBookingRequestVars);
// Variables can be defined inline as well.
const ref = upsertBookingRequestRef({ id: ..., spaceId: ..., spaceTitle: ..., spaceDistrict: ..., governorate: ..., ownerId: ..., ownerName: ..., ownerPhone: ..., practitionerId: ..., practitionerName: ..., practitionerEmail: ..., practitionerPhone: ..., practitionerSpecialty: ..., practitionerSyndicateNumber: ..., formulaJson: ..., startDate: ..., endDate: ..., selectedDays: ..., selectedStartHour: ..., selectedEndHour: ..., selectedShift: ..., selectedDateTimeRange: ..., durationMonths: ..., totalAmountUsd: ..., clinicalNotes: ..., status: ..., createdAt: ..., reviewedAt: ..., rejectionReason: ..., isExternalPaymentSettled: ..., subdivisionId: ..., subdivisionName: ..., selectedStrategy: ..., });

// You can also pass in a `DataConnect` instance to the `MutationRef` function.
const dataConnect = getDataConnect(connectorConfig);
const ref = upsertBookingRequestRef(dataConnect, upsertBookingRequestVars);

// Call `executeMutation()` on the reference to execute the mutation.
// You can use the `await` keyword to wait for the promise to resolve.
const { data } = await executeMutation(ref);

console.log(data.bookingRequest_upsert);

// Or, you can use the `Promise` API.
executeMutation(ref).then((response) => {
  const data = response.data;
  console.log(data.bookingRequest_upsert);
});
```

## InsertWhishTransaction
You can execute the `InsertWhishTransaction` mutation using the following action shortcut function, or by calling `executeMutation()` after calling the following `MutationRef` function, both of which are defined in [dataconnect-generated/index.d.ts](./index.d.ts):
```typescript
insertWhishTransaction(vars: InsertWhishTransactionVariables): MutationPromise<InsertWhishTransactionData, InsertWhishTransactionVariables>;

interface InsertWhishTransactionRef {
  ...
  /* Allow users to create refs without passing in DataConnect */
  (vars: InsertWhishTransactionVariables): MutationRef<InsertWhishTransactionData, InsertWhishTransactionVariables>;
}
export const insertWhishTransactionRef: InsertWhishTransactionRef;
```
You can also pass in a `DataConnect` instance to the action shortcut function or `MutationRef` function.
```typescript
insertWhishTransaction(dc: DataConnect, vars: InsertWhishTransactionVariables): MutationPromise<InsertWhishTransactionData, InsertWhishTransactionVariables>;

interface InsertWhishTransactionRef {
  ...
  (dc: DataConnect, vars: InsertWhishTransactionVariables): MutationRef<InsertWhishTransactionData, InsertWhishTransactionVariables>;
}
export const insertWhishTransactionRef: InsertWhishTransactionRef;
```

If you need the name of the operation without creating a ref, you can retrieve the operation name by calling the `operationName` property on the insertWhishTransactionRef:
```typescript
const name = insertWhishTransactionRef.operationName;
console.log(name);
```

### Variables
The `InsertWhishTransaction` mutation requires an argument of type `InsertWhishTransactionVariables`, which is defined in [dataconnect-generated/index.d.ts](./index.d.ts). It has the following fields:

```typescript
export interface InsertWhishTransactionVariables {
  id: string;
  orderId: string;
  amountUsd: number;
  currency: string;
  status: string;
  timestamp: Int64String;
  payerName: string;
  payerPhone: string;
  channelId: string;
  sourceEmail: string;
  signatureHash: string;
  spaceId: string;
  spaceTitle: string;
  daysGranted: number;
  userId: string;
}
```
### Return Type
Recall that executing the `InsertWhishTransaction` mutation returns a `MutationPromise` that resolves to an object with a `data` property.

The `data` property is an object of type `InsertWhishTransactionData`, which is defined in [dataconnect-generated/index.d.ts](./index.d.ts). It has the following fields:
```typescript
export interface InsertWhishTransactionData {
  whishTransaction_insert: WhishTransaction_Key;
}
```
### Using `InsertWhishTransaction`'s action shortcut function

```typescript
import { getDataConnect } from 'firebase/data-connect';
import { connectorConfig, insertWhishTransaction, InsertWhishTransactionVariables } from '@prohost/dataconnect-generated';

// The `InsertWhishTransaction` mutation requires an argument of type `InsertWhishTransactionVariables`:
const insertWhishTransactionVars: InsertWhishTransactionVariables = {
  id: ..., 
  orderId: ..., 
  amountUsd: ..., 
  currency: ..., 
  status: ..., 
  timestamp: ..., 
  payerName: ..., 
  payerPhone: ..., 
  channelId: ..., 
  sourceEmail: ..., 
  signatureHash: ..., 
  spaceId: ..., 
  spaceTitle: ..., 
  daysGranted: ..., 
  userId: ..., 
};

// Call the `insertWhishTransaction()` function to execute the mutation.
// You can use the `await` keyword to wait for the promise to resolve.
const { data } = await insertWhishTransaction(insertWhishTransactionVars);
// Variables can be defined inline as well.
const { data } = await insertWhishTransaction({ id: ..., orderId: ..., amountUsd: ..., currency: ..., status: ..., timestamp: ..., payerName: ..., payerPhone: ..., channelId: ..., sourceEmail: ..., signatureHash: ..., spaceId: ..., spaceTitle: ..., daysGranted: ..., userId: ..., });

// You can also pass in a `DataConnect` instance to the action shortcut function.
const dataConnect = getDataConnect(connectorConfig);
const { data } = await insertWhishTransaction(dataConnect, insertWhishTransactionVars);

console.log(data.whishTransaction_insert);

// Or, you can use the `Promise` API.
insertWhishTransaction(insertWhishTransactionVars).then((response) => {
  const data = response.data;
  console.log(data.whishTransaction_insert);
});
```

### Using `InsertWhishTransaction`'s `MutationRef` function

```typescript
import { getDataConnect, executeMutation } from 'firebase/data-connect';
import { connectorConfig, insertWhishTransactionRef, InsertWhishTransactionVariables } from '@prohost/dataconnect-generated';

// The `InsertWhishTransaction` mutation requires an argument of type `InsertWhishTransactionVariables`:
const insertWhishTransactionVars: InsertWhishTransactionVariables = {
  id: ..., 
  orderId: ..., 
  amountUsd: ..., 
  currency: ..., 
  status: ..., 
  timestamp: ..., 
  payerName: ..., 
  payerPhone: ..., 
  channelId: ..., 
  sourceEmail: ..., 
  signatureHash: ..., 
  spaceId: ..., 
  spaceTitle: ..., 
  daysGranted: ..., 
  userId: ..., 
};

// Call the `insertWhishTransactionRef()` function to get a reference to the mutation.
const ref = insertWhishTransactionRef(insertWhishTransactionVars);
// Variables can be defined inline as well.
const ref = insertWhishTransactionRef({ id: ..., orderId: ..., amountUsd: ..., currency: ..., status: ..., timestamp: ..., payerName: ..., payerPhone: ..., channelId: ..., sourceEmail: ..., signatureHash: ..., spaceId: ..., spaceTitle: ..., daysGranted: ..., userId: ..., });

// You can also pass in a `DataConnect` instance to the `MutationRef` function.
const dataConnect = getDataConnect(connectorConfig);
const ref = insertWhishTransactionRef(dataConnect, insertWhishTransactionVars);

// Call `executeMutation()` on the reference to execute the mutation.
// You can use the `await` keyword to wait for the promise to resolve.
const { data } = await executeMutation(ref);

console.log(data.whishTransaction_insert);

// Or, you can use the `Promise` API.
executeMutation(ref).then((response) => {
  const data = response.data;
  console.log(data.whishTransaction_insert);
});
```

## UpsertWhishTransaction
You can execute the `UpsertWhishTransaction` mutation using the following action shortcut function, or by calling `executeMutation()` after calling the following `MutationRef` function, both of which are defined in [dataconnect-generated/index.d.ts](./index.d.ts):
```typescript
upsertWhishTransaction(vars: UpsertWhishTransactionVariables): MutationPromise<UpsertWhishTransactionData, UpsertWhishTransactionVariables>;

interface UpsertWhishTransactionRef {
  ...
  /* Allow users to create refs without passing in DataConnect */
  (vars: UpsertWhishTransactionVariables): MutationRef<UpsertWhishTransactionData, UpsertWhishTransactionVariables>;
}
export const upsertWhishTransactionRef: UpsertWhishTransactionRef;
```
You can also pass in a `DataConnect` instance to the action shortcut function or `MutationRef` function.
```typescript
upsertWhishTransaction(dc: DataConnect, vars: UpsertWhishTransactionVariables): MutationPromise<UpsertWhishTransactionData, UpsertWhishTransactionVariables>;

interface UpsertWhishTransactionRef {
  ...
  (dc: DataConnect, vars: UpsertWhishTransactionVariables): MutationRef<UpsertWhishTransactionData, UpsertWhishTransactionVariables>;
}
export const upsertWhishTransactionRef: UpsertWhishTransactionRef;
```

If you need the name of the operation without creating a ref, you can retrieve the operation name by calling the `operationName` property on the upsertWhishTransactionRef:
```typescript
const name = upsertWhishTransactionRef.operationName;
console.log(name);
```

### Variables
The `UpsertWhishTransaction` mutation requires an argument of type `UpsertWhishTransactionVariables`, which is defined in [dataconnect-generated/index.d.ts](./index.d.ts). It has the following fields:

```typescript
export interface UpsertWhishTransactionVariables {
  id: string;
  orderId: string;
  amountUsd: number;
  currency: string;
  status: string;
  timestamp: Int64String;
  payerName: string;
  payerPhone: string;
  channelId: string;
  sourceEmail: string;
  signatureHash: string;
  spaceId: string;
  spaceTitle: string;
  daysGranted: number;
  userId: string;
}
```
### Return Type
Recall that executing the `UpsertWhishTransaction` mutation returns a `MutationPromise` that resolves to an object with a `data` property.

The `data` property is an object of type `UpsertWhishTransactionData`, which is defined in [dataconnect-generated/index.d.ts](./index.d.ts). It has the following fields:
```typescript
export interface UpsertWhishTransactionData {
  whishTransaction_upsert: WhishTransaction_Key;
}
```
### Using `UpsertWhishTransaction`'s action shortcut function

```typescript
import { getDataConnect } from 'firebase/data-connect';
import { connectorConfig, upsertWhishTransaction, UpsertWhishTransactionVariables } from '@prohost/dataconnect-generated';

// The `UpsertWhishTransaction` mutation requires an argument of type `UpsertWhishTransactionVariables`:
const upsertWhishTransactionVars: UpsertWhishTransactionVariables = {
  id: ..., 
  orderId: ..., 
  amountUsd: ..., 
  currency: ..., 
  status: ..., 
  timestamp: ..., 
  payerName: ..., 
  payerPhone: ..., 
  channelId: ..., 
  sourceEmail: ..., 
  signatureHash: ..., 
  spaceId: ..., 
  spaceTitle: ..., 
  daysGranted: ..., 
  userId: ..., 
};

// Call the `upsertWhishTransaction()` function to execute the mutation.
// You can use the `await` keyword to wait for the promise to resolve.
const { data } = await upsertWhishTransaction(upsertWhishTransactionVars);
// Variables can be defined inline as well.
const { data } = await upsertWhishTransaction({ id: ..., orderId: ..., amountUsd: ..., currency: ..., status: ..., timestamp: ..., payerName: ..., payerPhone: ..., channelId: ..., sourceEmail: ..., signatureHash: ..., spaceId: ..., spaceTitle: ..., daysGranted: ..., userId: ..., });

// You can also pass in a `DataConnect` instance to the action shortcut function.
const dataConnect = getDataConnect(connectorConfig);
const { data } = await upsertWhishTransaction(dataConnect, upsertWhishTransactionVars);

console.log(data.whishTransaction_upsert);

// Or, you can use the `Promise` API.
upsertWhishTransaction(upsertWhishTransactionVars).then((response) => {
  const data = response.data;
  console.log(data.whishTransaction_upsert);
});
```

### Using `UpsertWhishTransaction`'s `MutationRef` function

```typescript
import { getDataConnect, executeMutation } from 'firebase/data-connect';
import { connectorConfig, upsertWhishTransactionRef, UpsertWhishTransactionVariables } from '@prohost/dataconnect-generated';

// The `UpsertWhishTransaction` mutation requires an argument of type `UpsertWhishTransactionVariables`:
const upsertWhishTransactionVars: UpsertWhishTransactionVariables = {
  id: ..., 
  orderId: ..., 
  amountUsd: ..., 
  currency: ..., 
  status: ..., 
  timestamp: ..., 
  payerName: ..., 
  payerPhone: ..., 
  channelId: ..., 
  sourceEmail: ..., 
  signatureHash: ..., 
  spaceId: ..., 
  spaceTitle: ..., 
  daysGranted: ..., 
  userId: ..., 
};

// Call the `upsertWhishTransactionRef()` function to get a reference to the mutation.
const ref = upsertWhishTransactionRef(upsertWhishTransactionVars);
// Variables can be defined inline as well.
const ref = upsertWhishTransactionRef({ id: ..., orderId: ..., amountUsd: ..., currency: ..., status: ..., timestamp: ..., payerName: ..., payerPhone: ..., channelId: ..., sourceEmail: ..., signatureHash: ..., spaceId: ..., spaceTitle: ..., daysGranted: ..., userId: ..., });

// You can also pass in a `DataConnect` instance to the `MutationRef` function.
const dataConnect = getDataConnect(connectorConfig);
const ref = upsertWhishTransactionRef(dataConnect, upsertWhishTransactionVars);

// Call `executeMutation()` on the reference to execute the mutation.
// You can use the `await` keyword to wait for the promise to resolve.
const { data } = await executeMutation(ref);

console.log(data.whishTransaction_upsert);

// Or, you can use the `Promise` API.
executeMutation(ref).then((response) => {
  const data = response.data;
  console.log(data.whishTransaction_upsert);
});
```

## InsertAuditSecurityLog
You can execute the `InsertAuditSecurityLog` mutation using the following action shortcut function, or by calling `executeMutation()` after calling the following `MutationRef` function, both of which are defined in [dataconnect-generated/index.d.ts](./index.d.ts):
```typescript
insertAuditSecurityLog(vars: InsertAuditSecurityLogVariables): MutationPromise<InsertAuditSecurityLogData, InsertAuditSecurityLogVariables>;

interface InsertAuditSecurityLogRef {
  ...
  /* Allow users to create refs without passing in DataConnect */
  (vars: InsertAuditSecurityLogVariables): MutationRef<InsertAuditSecurityLogData, InsertAuditSecurityLogVariables>;
}
export const insertAuditSecurityLogRef: InsertAuditSecurityLogRef;
```
You can also pass in a `DataConnect` instance to the action shortcut function or `MutationRef` function.
```typescript
insertAuditSecurityLog(dc: DataConnect, vars: InsertAuditSecurityLogVariables): MutationPromise<InsertAuditSecurityLogData, InsertAuditSecurityLogVariables>;

interface InsertAuditSecurityLogRef {
  ...
  (dc: DataConnect, vars: InsertAuditSecurityLogVariables): MutationRef<InsertAuditSecurityLogData, InsertAuditSecurityLogVariables>;
}
export const insertAuditSecurityLogRef: InsertAuditSecurityLogRef;
```

If you need the name of the operation without creating a ref, you can retrieve the operation name by calling the `operationName` property on the insertAuditSecurityLogRef:
```typescript
const name = insertAuditSecurityLogRef.operationName;
console.log(name);
```

### Variables
The `InsertAuditSecurityLog` mutation requires an argument of type `InsertAuditSecurityLogVariables`, which is defined in [dataconnect-generated/index.d.ts](./index.d.ts). It has the following fields:

```typescript
export interface InsertAuditSecurityLogVariables {
  id: string;
  timestamp: Int64String;
  actionType: string;
  details: string;
  actorEmail: string;
  severity: string;
  ipAddress: string;
}
```
### Return Type
Recall that executing the `InsertAuditSecurityLog` mutation returns a `MutationPromise` that resolves to an object with a `data` property.

The `data` property is an object of type `InsertAuditSecurityLogData`, which is defined in [dataconnect-generated/index.d.ts](./index.d.ts). It has the following fields:
```typescript
export interface InsertAuditSecurityLogData {
  auditSecurityLog_insert: AuditSecurityLog_Key;
}
```
### Using `InsertAuditSecurityLog`'s action shortcut function

```typescript
import { getDataConnect } from 'firebase/data-connect';
import { connectorConfig, insertAuditSecurityLog, InsertAuditSecurityLogVariables } from '@prohost/dataconnect-generated';

// The `InsertAuditSecurityLog` mutation requires an argument of type `InsertAuditSecurityLogVariables`:
const insertAuditSecurityLogVars: InsertAuditSecurityLogVariables = {
  id: ..., 
  timestamp: ..., 
  actionType: ..., 
  details: ..., 
  actorEmail: ..., 
  severity: ..., 
  ipAddress: ..., 
};

// Call the `insertAuditSecurityLog()` function to execute the mutation.
// You can use the `await` keyword to wait for the promise to resolve.
const { data } = await insertAuditSecurityLog(insertAuditSecurityLogVars);
// Variables can be defined inline as well.
const { data } = await insertAuditSecurityLog({ id: ..., timestamp: ..., actionType: ..., details: ..., actorEmail: ..., severity: ..., ipAddress: ..., });

// You can also pass in a `DataConnect` instance to the action shortcut function.
const dataConnect = getDataConnect(connectorConfig);
const { data } = await insertAuditSecurityLog(dataConnect, insertAuditSecurityLogVars);

console.log(data.auditSecurityLog_insert);

// Or, you can use the `Promise` API.
insertAuditSecurityLog(insertAuditSecurityLogVars).then((response) => {
  const data = response.data;
  console.log(data.auditSecurityLog_insert);
});
```

### Using `InsertAuditSecurityLog`'s `MutationRef` function

```typescript
import { getDataConnect, executeMutation } from 'firebase/data-connect';
import { connectorConfig, insertAuditSecurityLogRef, InsertAuditSecurityLogVariables } from '@prohost/dataconnect-generated';

// The `InsertAuditSecurityLog` mutation requires an argument of type `InsertAuditSecurityLogVariables`:
const insertAuditSecurityLogVars: InsertAuditSecurityLogVariables = {
  id: ..., 
  timestamp: ..., 
  actionType: ..., 
  details: ..., 
  actorEmail: ..., 
  severity: ..., 
  ipAddress: ..., 
};

// Call the `insertAuditSecurityLogRef()` function to get a reference to the mutation.
const ref = insertAuditSecurityLogRef(insertAuditSecurityLogVars);
// Variables can be defined inline as well.
const ref = insertAuditSecurityLogRef({ id: ..., timestamp: ..., actionType: ..., details: ..., actorEmail: ..., severity: ..., ipAddress: ..., });

// You can also pass in a `DataConnect` instance to the `MutationRef` function.
const dataConnect = getDataConnect(connectorConfig);
const ref = insertAuditSecurityLogRef(dataConnect, insertAuditSecurityLogVars);

// Call `executeMutation()` on the reference to execute the mutation.
// You can use the `await` keyword to wait for the promise to resolve.
const { data } = await executeMutation(ref);

console.log(data.auditSecurityLog_insert);

// Or, you can use the `Promise` API.
executeMutation(ref).then((response) => {
  const data = response.data;
  console.log(data.auditSecurityLog_insert);
});
```

## UpsertAvatarCampaign
You can execute the `UpsertAvatarCampaign` mutation using the following action shortcut function, or by calling `executeMutation()` after calling the following `MutationRef` function, both of which are defined in [dataconnect-generated/index.d.ts](./index.d.ts):
```typescript
upsertAvatarCampaign(vars: UpsertAvatarCampaignVariables): MutationPromise<UpsertAvatarCampaignData, UpsertAvatarCampaignVariables>;

interface UpsertAvatarCampaignRef {
  ...
  /* Allow users to create refs without passing in DataConnect */
  (vars: UpsertAvatarCampaignVariables): MutationRef<UpsertAvatarCampaignData, UpsertAvatarCampaignVariables>;
}
export const upsertAvatarCampaignRef: UpsertAvatarCampaignRef;
```
You can also pass in a `DataConnect` instance to the action shortcut function or `MutationRef` function.
```typescript
upsertAvatarCampaign(dc: DataConnect, vars: UpsertAvatarCampaignVariables): MutationPromise<UpsertAvatarCampaignData, UpsertAvatarCampaignVariables>;

interface UpsertAvatarCampaignRef {
  ...
  (dc: DataConnect, vars: UpsertAvatarCampaignVariables): MutationRef<UpsertAvatarCampaignData, UpsertAvatarCampaignVariables>;
}
export const upsertAvatarCampaignRef: UpsertAvatarCampaignRef;
```

If you need the name of the operation without creating a ref, you can retrieve the operation name by calling the `operationName` property on the upsertAvatarCampaignRef:
```typescript
const name = upsertAvatarCampaignRef.operationName;
console.log(name);
```

### Variables
The `UpsertAvatarCampaign` mutation requires an argument of type `UpsertAvatarCampaignVariables`, which is defined in [dataconnect-generated/index.d.ts](./index.d.ts). It has the following fields:

```typescript
export interface UpsertAvatarCampaignVariables {
  id: string;
  spaceId: string;
  spaceTitle: string;
  instagramHandle: string;
  totalReelViews: number;
  linkClicks: number;
  inquiriesGenerated: number;
  generatedCaption: string;
  storyOverlayTag: string;
  lastNudgeText: string;
}
```
### Return Type
Recall that executing the `UpsertAvatarCampaign` mutation returns a `MutationPromise` that resolves to an object with a `data` property.

The `data` property is an object of type `UpsertAvatarCampaignData`, which is defined in [dataconnect-generated/index.d.ts](./index.d.ts). It has the following fields:
```typescript
export interface UpsertAvatarCampaignData {
  avatarCampaign_upsert: AvatarCampaign_Key;
}
```
### Using `UpsertAvatarCampaign`'s action shortcut function

```typescript
import { getDataConnect } from 'firebase/data-connect';
import { connectorConfig, upsertAvatarCampaign, UpsertAvatarCampaignVariables } from '@prohost/dataconnect-generated';

// The `UpsertAvatarCampaign` mutation requires an argument of type `UpsertAvatarCampaignVariables`:
const upsertAvatarCampaignVars: UpsertAvatarCampaignVariables = {
  id: ..., 
  spaceId: ..., 
  spaceTitle: ..., 
  instagramHandle: ..., 
  totalReelViews: ..., 
  linkClicks: ..., 
  inquiriesGenerated: ..., 
  generatedCaption: ..., 
  storyOverlayTag: ..., 
  lastNudgeText: ..., 
};

// Call the `upsertAvatarCampaign()` function to execute the mutation.
// You can use the `await` keyword to wait for the promise to resolve.
const { data } = await upsertAvatarCampaign(upsertAvatarCampaignVars);
// Variables can be defined inline as well.
const { data } = await upsertAvatarCampaign({ id: ..., spaceId: ..., spaceTitle: ..., instagramHandle: ..., totalReelViews: ..., linkClicks: ..., inquiriesGenerated: ..., generatedCaption: ..., storyOverlayTag: ..., lastNudgeText: ..., });

// You can also pass in a `DataConnect` instance to the action shortcut function.
const dataConnect = getDataConnect(connectorConfig);
const { data } = await upsertAvatarCampaign(dataConnect, upsertAvatarCampaignVars);

console.log(data.avatarCampaign_upsert);

// Or, you can use the `Promise` API.
upsertAvatarCampaign(upsertAvatarCampaignVars).then((response) => {
  const data = response.data;
  console.log(data.avatarCampaign_upsert);
});
```

### Using `UpsertAvatarCampaign`'s `MutationRef` function

```typescript
import { getDataConnect, executeMutation } from 'firebase/data-connect';
import { connectorConfig, upsertAvatarCampaignRef, UpsertAvatarCampaignVariables } from '@prohost/dataconnect-generated';

// The `UpsertAvatarCampaign` mutation requires an argument of type `UpsertAvatarCampaignVariables`:
const upsertAvatarCampaignVars: UpsertAvatarCampaignVariables = {
  id: ..., 
  spaceId: ..., 
  spaceTitle: ..., 
  instagramHandle: ..., 
  totalReelViews: ..., 
  linkClicks: ..., 
  inquiriesGenerated: ..., 
  generatedCaption: ..., 
  storyOverlayTag: ..., 
  lastNudgeText: ..., 
};

// Call the `upsertAvatarCampaignRef()` function to get a reference to the mutation.
const ref = upsertAvatarCampaignRef(upsertAvatarCampaignVars);
// Variables can be defined inline as well.
const ref = upsertAvatarCampaignRef({ id: ..., spaceId: ..., spaceTitle: ..., instagramHandle: ..., totalReelViews: ..., linkClicks: ..., inquiriesGenerated: ..., generatedCaption: ..., storyOverlayTag: ..., lastNudgeText: ..., });

// You can also pass in a `DataConnect` instance to the `MutationRef` function.
const dataConnect = getDataConnect(connectorConfig);
const ref = upsertAvatarCampaignRef(dataConnect, upsertAvatarCampaignVars);

// Call `executeMutation()` on the reference to execute the mutation.
// You can use the `await` keyword to wait for the promise to resolve.
const { data } = await executeMutation(ref);

console.log(data.avatarCampaign_upsert);

// Or, you can use the `Promise` API.
executeMutation(ref).then((response) => {
  const data = response.data;
  console.log(data.avatarCampaign_upsert);
});
```

## UpsertAdminPricingState
You can execute the `UpsertAdminPricingState` mutation using the following action shortcut function, or by calling `executeMutation()` after calling the following `MutationRef` function, both of which are defined in [dataconnect-generated/index.d.ts](./index.d.ts):
```typescript
upsertAdminPricingState(vars: UpsertAdminPricingStateVariables): MutationPromise<UpsertAdminPricingStateData, UpsertAdminPricingStateVariables>;

interface UpsertAdminPricingStateRef {
  ...
  /* Allow users to create refs without passing in DataConnect */
  (vars: UpsertAdminPricingStateVariables): MutationRef<UpsertAdminPricingStateData, UpsertAdminPricingStateVariables>;
}
export const upsertAdminPricingStateRef: UpsertAdminPricingStateRef;
```
You can also pass in a `DataConnect` instance to the action shortcut function or `MutationRef` function.
```typescript
upsertAdminPricingState(dc: DataConnect, vars: UpsertAdminPricingStateVariables): MutationPromise<UpsertAdminPricingStateData, UpsertAdminPricingStateVariables>;

interface UpsertAdminPricingStateRef {
  ...
  (dc: DataConnect, vars: UpsertAdminPricingStateVariables): MutationRef<UpsertAdminPricingStateData, UpsertAdminPricingStateVariables>;
}
export const upsertAdminPricingStateRef: UpsertAdminPricingStateRef;
```

If you need the name of the operation without creating a ref, you can retrieve the operation name by calling the `operationName` property on the upsertAdminPricingStateRef:
```typescript
const name = upsertAdminPricingStateRef.operationName;
console.log(name);
```

### Variables
The `UpsertAdminPricingState` mutation requires an argument of type `UpsertAdminPricingStateVariables`, which is defined in [dataconnect-generated/index.d.ts](./index.d.ts). It has the following fields:

```typescript
export interface UpsertAdminPricingStateVariables {
  id: string;
  monthlySubscriptionFeeUsd: number;
  baselineFeeUsd: number;
  presetOptions: number[];
  merchantChannelId: string;
  merchantSource: string;
  merchantSecretKeyMasked: string;
}
```
### Return Type
Recall that executing the `UpsertAdminPricingState` mutation returns a `MutationPromise` that resolves to an object with a `data` property.

The `data` property is an object of type `UpsertAdminPricingStateData`, which is defined in [dataconnect-generated/index.d.ts](./index.d.ts). It has the following fields:
```typescript
export interface UpsertAdminPricingStateData {
  adminPricingState_upsert: AdminPricingState_Key;
}
```
### Using `UpsertAdminPricingState`'s action shortcut function

```typescript
import { getDataConnect } from 'firebase/data-connect';
import { connectorConfig, upsertAdminPricingState, UpsertAdminPricingStateVariables } from '@prohost/dataconnect-generated';

// The `UpsertAdminPricingState` mutation requires an argument of type `UpsertAdminPricingStateVariables`:
const upsertAdminPricingStateVars: UpsertAdminPricingStateVariables = {
  id: ..., 
  monthlySubscriptionFeeUsd: ..., 
  baselineFeeUsd: ..., 
  presetOptions: ..., 
  merchantChannelId: ..., 
  merchantSource: ..., 
  merchantSecretKeyMasked: ..., 
};

// Call the `upsertAdminPricingState()` function to execute the mutation.
// You can use the `await` keyword to wait for the promise to resolve.
const { data } = await upsertAdminPricingState(upsertAdminPricingStateVars);
// Variables can be defined inline as well.
const { data } = await upsertAdminPricingState({ id: ..., monthlySubscriptionFeeUsd: ..., baselineFeeUsd: ..., presetOptions: ..., merchantChannelId: ..., merchantSource: ..., merchantSecretKeyMasked: ..., });

// You can also pass in a `DataConnect` instance to the action shortcut function.
const dataConnect = getDataConnect(connectorConfig);
const { data } = await upsertAdminPricingState(dataConnect, upsertAdminPricingStateVars);

console.log(data.adminPricingState_upsert);

// Or, you can use the `Promise` API.
upsertAdminPricingState(upsertAdminPricingStateVars).then((response) => {
  const data = response.data;
  console.log(data.adminPricingState_upsert);
});
```

### Using `UpsertAdminPricingState`'s `MutationRef` function

```typescript
import { getDataConnect, executeMutation } from 'firebase/data-connect';
import { connectorConfig, upsertAdminPricingStateRef, UpsertAdminPricingStateVariables } from '@prohost/dataconnect-generated';

// The `UpsertAdminPricingState` mutation requires an argument of type `UpsertAdminPricingStateVariables`:
const upsertAdminPricingStateVars: UpsertAdminPricingStateVariables = {
  id: ..., 
  monthlySubscriptionFeeUsd: ..., 
  baselineFeeUsd: ..., 
  presetOptions: ..., 
  merchantChannelId: ..., 
  merchantSource: ..., 
  merchantSecretKeyMasked: ..., 
};

// Call the `upsertAdminPricingStateRef()` function to get a reference to the mutation.
const ref = upsertAdminPricingStateRef(upsertAdminPricingStateVars);
// Variables can be defined inline as well.
const ref = upsertAdminPricingStateRef({ id: ..., monthlySubscriptionFeeUsd: ..., baselineFeeUsd: ..., presetOptions: ..., merchantChannelId: ..., merchantSource: ..., merchantSecretKeyMasked: ..., });

// You can also pass in a `DataConnect` instance to the `MutationRef` function.
const dataConnect = getDataConnect(connectorConfig);
const ref = upsertAdminPricingStateRef(dataConnect, upsertAdminPricingStateVars);

// Call `executeMutation()` on the reference to execute the mutation.
// You can use the `await` keyword to wait for the promise to resolve.
const { data } = await executeMutation(ref);

console.log(data.adminPricingState_upsert);

// Or, you can use the `Promise` API.
executeMutation(ref).then((response) => {
  const data = response.data;
  console.log(data.adminPricingState_upsert);
});
```

## DeleteSpaceListing
You can execute the `DeleteSpaceListing` mutation using the following action shortcut function, or by calling `executeMutation()` after calling the following `MutationRef` function, both of which are defined in [dataconnect-generated/index.d.ts](./index.d.ts):
```typescript
deleteSpaceListing(vars: DeleteSpaceListingVariables): MutationPromise<DeleteSpaceListingData, DeleteSpaceListingVariables>;

interface DeleteSpaceListingRef {
  ...
  /* Allow users to create refs without passing in DataConnect */
  (vars: DeleteSpaceListingVariables): MutationRef<DeleteSpaceListingData, DeleteSpaceListingVariables>;
}
export const deleteSpaceListingRef: DeleteSpaceListingRef;
```
You can also pass in a `DataConnect` instance to the action shortcut function or `MutationRef` function.
```typescript
deleteSpaceListing(dc: DataConnect, vars: DeleteSpaceListingVariables): MutationPromise<DeleteSpaceListingData, DeleteSpaceListingVariables>;

interface DeleteSpaceListingRef {
  ...
  (dc: DataConnect, vars: DeleteSpaceListingVariables): MutationRef<DeleteSpaceListingData, DeleteSpaceListingVariables>;
}
export const deleteSpaceListingRef: DeleteSpaceListingRef;
```

If you need the name of the operation without creating a ref, you can retrieve the operation name by calling the `operationName` property on the deleteSpaceListingRef:
```typescript
const name = deleteSpaceListingRef.operationName;
console.log(name);
```

### Variables
The `DeleteSpaceListing` mutation requires an argument of type `DeleteSpaceListingVariables`, which is defined in [dataconnect-generated/index.d.ts](./index.d.ts). It has the following fields:

```typescript
export interface DeleteSpaceListingVariables {
  id: string;
}
```
### Return Type
Recall that executing the `DeleteSpaceListing` mutation returns a `MutationPromise` that resolves to an object with a `data` property.

The `data` property is an object of type `DeleteSpaceListingData`, which is defined in [dataconnect-generated/index.d.ts](./index.d.ts). It has the following fields:
```typescript
export interface DeleteSpaceListingData {
  spaceListing_delete?: SpaceListing_Key | null;
}
```
### Using `DeleteSpaceListing`'s action shortcut function

```typescript
import { getDataConnect } from 'firebase/data-connect';
import { connectorConfig, deleteSpaceListing, DeleteSpaceListingVariables } from '@prohost/dataconnect-generated';

// The `DeleteSpaceListing` mutation requires an argument of type `DeleteSpaceListingVariables`:
const deleteSpaceListingVars: DeleteSpaceListingVariables = {
  id: ..., 
};

// Call the `deleteSpaceListing()` function to execute the mutation.
// You can use the `await` keyword to wait for the promise to resolve.
const { data } = await deleteSpaceListing(deleteSpaceListingVars);
// Variables can be defined inline as well.
const { data } = await deleteSpaceListing({ id: ..., });

// You can also pass in a `DataConnect` instance to the action shortcut function.
const dataConnect = getDataConnect(connectorConfig);
const { data } = await deleteSpaceListing(dataConnect, deleteSpaceListingVars);

console.log(data.spaceListing_delete);

// Or, you can use the `Promise` API.
deleteSpaceListing(deleteSpaceListingVars).then((response) => {
  const data = response.data;
  console.log(data.spaceListing_delete);
});
```

### Using `DeleteSpaceListing`'s `MutationRef` function

```typescript
import { getDataConnect, executeMutation } from 'firebase/data-connect';
import { connectorConfig, deleteSpaceListingRef, DeleteSpaceListingVariables } from '@prohost/dataconnect-generated';

// The `DeleteSpaceListing` mutation requires an argument of type `DeleteSpaceListingVariables`:
const deleteSpaceListingVars: DeleteSpaceListingVariables = {
  id: ..., 
};

// Call the `deleteSpaceListingRef()` function to get a reference to the mutation.
const ref = deleteSpaceListingRef(deleteSpaceListingVars);
// Variables can be defined inline as well.
const ref = deleteSpaceListingRef({ id: ..., });

// You can also pass in a `DataConnect` instance to the `MutationRef` function.
const dataConnect = getDataConnect(connectorConfig);
const ref = deleteSpaceListingRef(dataConnect, deleteSpaceListingVars);

// Call `executeMutation()` on the reference to execute the mutation.
// You can use the `await` keyword to wait for the promise to resolve.
const { data } = await executeMutation(ref);

console.log(data.spaceListing_delete);

// Or, you can use the `Promise` API.
executeMutation(ref).then((response) => {
  const data = response.data;
  console.log(data.spaceListing_delete);
});
```

## DeleteBookingRequest
You can execute the `DeleteBookingRequest` mutation using the following action shortcut function, or by calling `executeMutation()` after calling the following `MutationRef` function, both of which are defined in [dataconnect-generated/index.d.ts](./index.d.ts):
```typescript
deleteBookingRequest(vars: DeleteBookingRequestVariables): MutationPromise<DeleteBookingRequestData, DeleteBookingRequestVariables>;

interface DeleteBookingRequestRef {
  ...
  /* Allow users to create refs without passing in DataConnect */
  (vars: DeleteBookingRequestVariables): MutationRef<DeleteBookingRequestData, DeleteBookingRequestVariables>;
}
export const deleteBookingRequestRef: DeleteBookingRequestRef;
```
You can also pass in a `DataConnect` instance to the action shortcut function or `MutationRef` function.
```typescript
deleteBookingRequest(dc: DataConnect, vars: DeleteBookingRequestVariables): MutationPromise<DeleteBookingRequestData, DeleteBookingRequestVariables>;

interface DeleteBookingRequestRef {
  ...
  (dc: DataConnect, vars: DeleteBookingRequestVariables): MutationRef<DeleteBookingRequestData, DeleteBookingRequestVariables>;
}
export const deleteBookingRequestRef: DeleteBookingRequestRef;
```

If you need the name of the operation without creating a ref, you can retrieve the operation name by calling the `operationName` property on the deleteBookingRequestRef:
```typescript
const name = deleteBookingRequestRef.operationName;
console.log(name);
```

### Variables
The `DeleteBookingRequest` mutation requires an argument of type `DeleteBookingRequestVariables`, which is defined in [dataconnect-generated/index.d.ts](./index.d.ts). It has the following fields:

```typescript
export interface DeleteBookingRequestVariables {
  id: string;
}
```
### Return Type
Recall that executing the `DeleteBookingRequest` mutation returns a `MutationPromise` that resolves to an object with a `data` property.

The `data` property is an object of type `DeleteBookingRequestData`, which is defined in [dataconnect-generated/index.d.ts](./index.d.ts). It has the following fields:
```typescript
export interface DeleteBookingRequestData {
  bookingRequest_delete?: BookingRequest_Key | null;
}
```
### Using `DeleteBookingRequest`'s action shortcut function

```typescript
import { getDataConnect } from 'firebase/data-connect';
import { connectorConfig, deleteBookingRequest, DeleteBookingRequestVariables } from '@prohost/dataconnect-generated';

// The `DeleteBookingRequest` mutation requires an argument of type `DeleteBookingRequestVariables`:
const deleteBookingRequestVars: DeleteBookingRequestVariables = {
  id: ..., 
};

// Call the `deleteBookingRequest()` function to execute the mutation.
// You can use the `await` keyword to wait for the promise to resolve.
const { data } = await deleteBookingRequest(deleteBookingRequestVars);
// Variables can be defined inline as well.
const { data } = await deleteBookingRequest({ id: ..., });

// You can also pass in a `DataConnect` instance to the action shortcut function.
const dataConnect = getDataConnect(connectorConfig);
const { data } = await deleteBookingRequest(dataConnect, deleteBookingRequestVars);

console.log(data.bookingRequest_delete);

// Or, you can use the `Promise` API.
deleteBookingRequest(deleteBookingRequestVars).then((response) => {
  const data = response.data;
  console.log(data.bookingRequest_delete);
});
```

### Using `DeleteBookingRequest`'s `MutationRef` function

```typescript
import { getDataConnect, executeMutation } from 'firebase/data-connect';
import { connectorConfig, deleteBookingRequestRef, DeleteBookingRequestVariables } from '@prohost/dataconnect-generated';

// The `DeleteBookingRequest` mutation requires an argument of type `DeleteBookingRequestVariables`:
const deleteBookingRequestVars: DeleteBookingRequestVariables = {
  id: ..., 
};

// Call the `deleteBookingRequestRef()` function to get a reference to the mutation.
const ref = deleteBookingRequestRef(deleteBookingRequestVars);
// Variables can be defined inline as well.
const ref = deleteBookingRequestRef({ id: ..., });

// You can also pass in a `DataConnect` instance to the `MutationRef` function.
const dataConnect = getDataConnect(connectorConfig);
const ref = deleteBookingRequestRef(dataConnect, deleteBookingRequestVars);

// Call `executeMutation()` on the reference to execute the mutation.
// You can use the `await` keyword to wait for the promise to resolve.
const { data } = await executeMutation(ref);

console.log(data.bookingRequest_delete);

// Or, you can use the `Promise` API.
executeMutation(ref).then((response) => {
  const data = response.data;
  console.log(data.bookingRequest_delete);
});
```

