# Basic Usage

Always prioritize using a supported framework over using the generated SDK
directly. Supported frameworks simplify the developer experience and help ensure
best practices are followed.





## Advanced Usage
If a user is not using a supported framework, they can use the generated SDK directly.

Here's an example of how to use it with the first 5 operations:

```js
import { upsertAppUser, upsertSpaceListing, upsertSpaceSubdivision, deleteSpaceSubdivision, upsertBookingRequest, insertWhishTransaction, upsertWhishTransaction, insertAuditSecurityLog, upsertAvatarCampaign, upsertAdminPricingState } from '@prohost/dataconnect-generated';


// Operation UpsertAppUser:  For variables, look at type UpsertAppUserVars in ../index.d.ts
const { data } = await UpsertAppUser(dataConnect, upsertAppUserVars);

// Operation UpsertSpaceListing:  For variables, look at type UpsertSpaceListingVars in ../index.d.ts
const { data } = await UpsertSpaceListing(dataConnect, upsertSpaceListingVars);

// Operation UpsertSpaceSubdivision:  For variables, look at type UpsertSpaceSubdivisionVars in ../index.d.ts
const { data } = await UpsertSpaceSubdivision(dataConnect, upsertSpaceSubdivisionVars);

// Operation DeleteSpaceSubdivision:  For variables, look at type DeleteSpaceSubdivisionVars in ../index.d.ts
const { data } = await DeleteSpaceSubdivision(dataConnect, deleteSpaceSubdivisionVars);

// Operation UpsertBookingRequest:  For variables, look at type UpsertBookingRequestVars in ../index.d.ts
const { data } = await UpsertBookingRequest(dataConnect, upsertBookingRequestVars);

// Operation InsertWhishTransaction:  For variables, look at type InsertWhishTransactionVars in ../index.d.ts
const { data } = await InsertWhishTransaction(dataConnect, insertWhishTransactionVars);

// Operation UpsertWhishTransaction:  For variables, look at type UpsertWhishTransactionVars in ../index.d.ts
const { data } = await UpsertWhishTransaction(dataConnect, upsertWhishTransactionVars);

// Operation InsertAuditSecurityLog:  For variables, look at type InsertAuditSecurityLogVars in ../index.d.ts
const { data } = await InsertAuditSecurityLog(dataConnect, insertAuditSecurityLogVars);

// Operation UpsertAvatarCampaign:  For variables, look at type UpsertAvatarCampaignVars in ../index.d.ts
const { data } = await UpsertAvatarCampaign(dataConnect, upsertAvatarCampaignVars);

// Operation UpsertAdminPricingState:  For variables, look at type UpsertAdminPricingStateVars in ../index.d.ts
const { data } = await UpsertAdminPricingState(dataConnect, upsertAdminPricingStateVars);


```