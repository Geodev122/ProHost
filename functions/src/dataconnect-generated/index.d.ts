import { ConnectorConfig, DataConnect, QueryRef, QueryPromise, ExecuteQueryOptions, MutationRef, MutationPromise } from 'firebase/data-connect';

export const connectorConfig: ConnectorConfig;

export type TimestampString = string;
export type UUIDString = string;
export type Int64String = string;
export type DateString = string;




export interface AdminPricingState_Key {
  id: string;
  __typename?: 'AdminPricingState_Key';
}

export interface AppUser_Key {
  id: string;
  __typename?: 'AppUser_Key';
}

export interface AuditSecurityLog_Key {
  id: string;
  __typename?: 'AuditSecurityLog_Key';
}

export interface AvatarCampaign_Key {
  id: string;
  __typename?: 'AvatarCampaign_Key';
}

export interface BookingRequest_Key {
  id: string;
  __typename?: 'BookingRequest_Key';
}

export interface DeleteBookingRequestData {
  bookingRequest_delete?: BookingRequest_Key | null;
}

export interface DeleteBookingRequestVariables {
  id: string;
}

export interface DeleteSpaceListingData {
  spaceListing_delete?: SpaceListing_Key | null;
}

export interface DeleteSpaceListingVariables {
  id: string;
}

export interface DeleteSpaceSubdivisionData {
  spaceSubdivision_delete?: SpaceSubdivision_Key | null;
}

export interface DeleteSpaceSubdivisionVariables {
  id: string;
}

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

export interface GetAppUserByIdVariables {
  id: string;
}

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

export interface GetSpaceSubdivisionsBySpaceIdVariables {
  spaceId: string;
}

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

export interface GetSuccessfulTransactionsInRangeVariables {
  startTimestamp: Int64String;
  endTimestamp: Int64String;
}

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

export interface InsertAuditSecurityLogData {
  auditSecurityLog_insert: AuditSecurityLog_Key;
}

export interface InsertAuditSecurityLogVariables {
  id: string;
  timestamp: Int64String;
  actionType: string;
  details: string;
  actorEmail: string;
  severity: string;
  ipAddress: string;
}

export interface InsertWhishTransactionData {
  whishTransaction_insert: WhishTransaction_Key;
}

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

export interface SpaceListing_Key {
  id: string;
  __typename?: 'SpaceListing_Key';
}

export interface SpaceSubdivision_Key {
  id: string;
  __typename?: 'SpaceSubdivision_Key';
}

export interface UpsertAdminPricingStateData {
  adminPricingState_upsert: AdminPricingState_Key;
}

export interface UpsertAdminPricingStateVariables {
  id: string;
  monthlySubscriptionFeeUsd: number;
  baselineFeeUsd: number;
  presetOptions: number[];
  merchantChannelId: string;
  merchantSource: string;
  merchantSecretKeyMasked: string;
}

export interface UpsertAppUserData {
  appUser_upsert: AppUser_Key;
}

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

export interface UpsertAvatarCampaignData {
  avatarCampaign_upsert: AvatarCampaign_Key;
}

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

export interface UpsertBookingRequestData {
  bookingRequest_upsert: BookingRequest_Key;
}

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

export interface UpsertSpaceListingData {
  spaceListing_upsert: SpaceListing_Key;
}

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

export interface UpsertSpaceSubdivisionData {
  spaceSubdivision_upsert: SpaceSubdivision_Key;
}

export interface UpsertSpaceSubdivisionVariables {
  id: string;
  spaceId: string;
  name: string;
  type: string;
  amenities: string[];
  strategiesJson: string;
}

export interface UpsertWhishTransactionData {
  whishTransaction_upsert: WhishTransaction_Key;
}

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

export interface WhishTransaction_Key {
  id: string;
  __typename?: 'WhishTransaction_Key';
}

interface UpsertAppUserRef {
  /* Allow users to create refs without passing in DataConnect */
  (vars: UpsertAppUserVariables): MutationRef<UpsertAppUserData, UpsertAppUserVariables>;
  /* Allow users to pass in custom DataConnect instances */
  (dc: DataConnect, vars: UpsertAppUserVariables): MutationRef<UpsertAppUserData, UpsertAppUserVariables>;
  operationName: string;
}
export const upsertAppUserRef: UpsertAppUserRef;

export function upsertAppUser(vars: UpsertAppUserVariables): MutationPromise<UpsertAppUserData, UpsertAppUserVariables>;
export function upsertAppUser(dc: DataConnect, vars: UpsertAppUserVariables): MutationPromise<UpsertAppUserData, UpsertAppUserVariables>;

interface UpsertSpaceListingRef {
  /* Allow users to create refs without passing in DataConnect */
  (vars: UpsertSpaceListingVariables): MutationRef<UpsertSpaceListingData, UpsertSpaceListingVariables>;
  /* Allow users to pass in custom DataConnect instances */
  (dc: DataConnect, vars: UpsertSpaceListingVariables): MutationRef<UpsertSpaceListingData, UpsertSpaceListingVariables>;
  operationName: string;
}
export const upsertSpaceListingRef: UpsertSpaceListingRef;

export function upsertSpaceListing(vars: UpsertSpaceListingVariables): MutationPromise<UpsertSpaceListingData, UpsertSpaceListingVariables>;
export function upsertSpaceListing(dc: DataConnect, vars: UpsertSpaceListingVariables): MutationPromise<UpsertSpaceListingData, UpsertSpaceListingVariables>;

interface UpsertSpaceSubdivisionRef {
  /* Allow users to create refs without passing in DataConnect */
  (vars: UpsertSpaceSubdivisionVariables): MutationRef<UpsertSpaceSubdivisionData, UpsertSpaceSubdivisionVariables>;
  /* Allow users to pass in custom DataConnect instances */
  (dc: DataConnect, vars: UpsertSpaceSubdivisionVariables): MutationRef<UpsertSpaceSubdivisionData, UpsertSpaceSubdivisionVariables>;
  operationName: string;
}
export const upsertSpaceSubdivisionRef: UpsertSpaceSubdivisionRef;

export function upsertSpaceSubdivision(vars: UpsertSpaceSubdivisionVariables): MutationPromise<UpsertSpaceSubdivisionData, UpsertSpaceSubdivisionVariables>;
export function upsertSpaceSubdivision(dc: DataConnect, vars: UpsertSpaceSubdivisionVariables): MutationPromise<UpsertSpaceSubdivisionData, UpsertSpaceSubdivisionVariables>;

interface DeleteSpaceSubdivisionRef {
  /* Allow users to create refs without passing in DataConnect */
  (vars: DeleteSpaceSubdivisionVariables): MutationRef<DeleteSpaceSubdivisionData, DeleteSpaceSubdivisionVariables>;
  /* Allow users to pass in custom DataConnect instances */
  (dc: DataConnect, vars: DeleteSpaceSubdivisionVariables): MutationRef<DeleteSpaceSubdivisionData, DeleteSpaceSubdivisionVariables>;
  operationName: string;
}
export const deleteSpaceSubdivisionRef: DeleteSpaceSubdivisionRef;

export function deleteSpaceSubdivision(vars: DeleteSpaceSubdivisionVariables): MutationPromise<DeleteSpaceSubdivisionData, DeleteSpaceSubdivisionVariables>;
export function deleteSpaceSubdivision(dc: DataConnect, vars: DeleteSpaceSubdivisionVariables): MutationPromise<DeleteSpaceSubdivisionData, DeleteSpaceSubdivisionVariables>;

interface UpsertBookingRequestRef {
  /* Allow users to create refs without passing in DataConnect */
  (vars: UpsertBookingRequestVariables): MutationRef<UpsertBookingRequestData, UpsertBookingRequestVariables>;
  /* Allow users to pass in custom DataConnect instances */
  (dc: DataConnect, vars: UpsertBookingRequestVariables): MutationRef<UpsertBookingRequestData, UpsertBookingRequestVariables>;
  operationName: string;
}
export const upsertBookingRequestRef: UpsertBookingRequestRef;

export function upsertBookingRequest(vars: UpsertBookingRequestVariables): MutationPromise<UpsertBookingRequestData, UpsertBookingRequestVariables>;
export function upsertBookingRequest(dc: DataConnect, vars: UpsertBookingRequestVariables): MutationPromise<UpsertBookingRequestData, UpsertBookingRequestVariables>;

interface InsertWhishTransactionRef {
  /* Allow users to create refs without passing in DataConnect */
  (vars: InsertWhishTransactionVariables): MutationRef<InsertWhishTransactionData, InsertWhishTransactionVariables>;
  /* Allow users to pass in custom DataConnect instances */
  (dc: DataConnect, vars: InsertWhishTransactionVariables): MutationRef<InsertWhishTransactionData, InsertWhishTransactionVariables>;
  operationName: string;
}
export const insertWhishTransactionRef: InsertWhishTransactionRef;

export function insertWhishTransaction(vars: InsertWhishTransactionVariables): MutationPromise<InsertWhishTransactionData, InsertWhishTransactionVariables>;
export function insertWhishTransaction(dc: DataConnect, vars: InsertWhishTransactionVariables): MutationPromise<InsertWhishTransactionData, InsertWhishTransactionVariables>;

interface UpsertWhishTransactionRef {
  /* Allow users to create refs without passing in DataConnect */
  (vars: UpsertWhishTransactionVariables): MutationRef<UpsertWhishTransactionData, UpsertWhishTransactionVariables>;
  /* Allow users to pass in custom DataConnect instances */
  (dc: DataConnect, vars: UpsertWhishTransactionVariables): MutationRef<UpsertWhishTransactionData, UpsertWhishTransactionVariables>;
  operationName: string;
}
export const upsertWhishTransactionRef: UpsertWhishTransactionRef;

export function upsertWhishTransaction(vars: UpsertWhishTransactionVariables): MutationPromise<UpsertWhishTransactionData, UpsertWhishTransactionVariables>;
export function upsertWhishTransaction(dc: DataConnect, vars: UpsertWhishTransactionVariables): MutationPromise<UpsertWhishTransactionData, UpsertWhishTransactionVariables>;

interface InsertAuditSecurityLogRef {
  /* Allow users to create refs without passing in DataConnect */
  (vars: InsertAuditSecurityLogVariables): MutationRef<InsertAuditSecurityLogData, InsertAuditSecurityLogVariables>;
  /* Allow users to pass in custom DataConnect instances */
  (dc: DataConnect, vars: InsertAuditSecurityLogVariables): MutationRef<InsertAuditSecurityLogData, InsertAuditSecurityLogVariables>;
  operationName: string;
}
export const insertAuditSecurityLogRef: InsertAuditSecurityLogRef;

export function insertAuditSecurityLog(vars: InsertAuditSecurityLogVariables): MutationPromise<InsertAuditSecurityLogData, InsertAuditSecurityLogVariables>;
export function insertAuditSecurityLog(dc: DataConnect, vars: InsertAuditSecurityLogVariables): MutationPromise<InsertAuditSecurityLogData, InsertAuditSecurityLogVariables>;

interface UpsertAvatarCampaignRef {
  /* Allow users to create refs without passing in DataConnect */
  (vars: UpsertAvatarCampaignVariables): MutationRef<UpsertAvatarCampaignData, UpsertAvatarCampaignVariables>;
  /* Allow users to pass in custom DataConnect instances */
  (dc: DataConnect, vars: UpsertAvatarCampaignVariables): MutationRef<UpsertAvatarCampaignData, UpsertAvatarCampaignVariables>;
  operationName: string;
}
export const upsertAvatarCampaignRef: UpsertAvatarCampaignRef;

export function upsertAvatarCampaign(vars: UpsertAvatarCampaignVariables): MutationPromise<UpsertAvatarCampaignData, UpsertAvatarCampaignVariables>;
export function upsertAvatarCampaign(dc: DataConnect, vars: UpsertAvatarCampaignVariables): MutationPromise<UpsertAvatarCampaignData, UpsertAvatarCampaignVariables>;

interface UpsertAdminPricingStateRef {
  /* Allow users to create refs without passing in DataConnect */
  (vars: UpsertAdminPricingStateVariables): MutationRef<UpsertAdminPricingStateData, UpsertAdminPricingStateVariables>;
  /* Allow users to pass in custom DataConnect instances */
  (dc: DataConnect, vars: UpsertAdminPricingStateVariables): MutationRef<UpsertAdminPricingStateData, UpsertAdminPricingStateVariables>;
  operationName: string;
}
export const upsertAdminPricingStateRef: UpsertAdminPricingStateRef;

export function upsertAdminPricingState(vars: UpsertAdminPricingStateVariables): MutationPromise<UpsertAdminPricingStateData, UpsertAdminPricingStateVariables>;
export function upsertAdminPricingState(dc: DataConnect, vars: UpsertAdminPricingStateVariables): MutationPromise<UpsertAdminPricingStateData, UpsertAdminPricingStateVariables>;

interface DeleteSpaceListingRef {
  /* Allow users to create refs without passing in DataConnect */
  (vars: DeleteSpaceListingVariables): MutationRef<DeleteSpaceListingData, DeleteSpaceListingVariables>;
  /* Allow users to pass in custom DataConnect instances */
  (dc: DataConnect, vars: DeleteSpaceListingVariables): MutationRef<DeleteSpaceListingData, DeleteSpaceListingVariables>;
  operationName: string;
}
export const deleteSpaceListingRef: DeleteSpaceListingRef;

export function deleteSpaceListing(vars: DeleteSpaceListingVariables): MutationPromise<DeleteSpaceListingData, DeleteSpaceListingVariables>;
export function deleteSpaceListing(dc: DataConnect, vars: DeleteSpaceListingVariables): MutationPromise<DeleteSpaceListingData, DeleteSpaceListingVariables>;

interface DeleteBookingRequestRef {
  /* Allow users to create refs without passing in DataConnect */
  (vars: DeleteBookingRequestVariables): MutationRef<DeleteBookingRequestData, DeleteBookingRequestVariables>;
  /* Allow users to pass in custom DataConnect instances */
  (dc: DataConnect, vars: DeleteBookingRequestVariables): MutationRef<DeleteBookingRequestData, DeleteBookingRequestVariables>;
  operationName: string;
}
export const deleteBookingRequestRef: DeleteBookingRequestRef;

export function deleteBookingRequest(vars: DeleteBookingRequestVariables): MutationPromise<DeleteBookingRequestData, DeleteBookingRequestVariables>;
export function deleteBookingRequest(dc: DataConnect, vars: DeleteBookingRequestVariables): MutationPromise<DeleteBookingRequestData, DeleteBookingRequestVariables>;

interface GetAppUsersRef {
  /* Allow users to create refs without passing in DataConnect */
  (): QueryRef<GetAppUsersData, undefined>;
  /* Allow users to pass in custom DataConnect instances */
  (dc: DataConnect): QueryRef<GetAppUsersData, undefined>;
  operationName: string;
}
export const getAppUsersRef: GetAppUsersRef;

export function getAppUsers(options?: ExecuteQueryOptions): QueryPromise<GetAppUsersData, undefined>;
export function getAppUsers(dc: DataConnect, options?: ExecuteQueryOptions): QueryPromise<GetAppUsersData, undefined>;

interface GetAppUserByIdRef {
  /* Allow users to create refs without passing in DataConnect */
  (vars: GetAppUserByIdVariables): QueryRef<GetAppUserByIdData, GetAppUserByIdVariables>;
  /* Allow users to pass in custom DataConnect instances */
  (dc: DataConnect, vars: GetAppUserByIdVariables): QueryRef<GetAppUserByIdData, GetAppUserByIdVariables>;
  operationName: string;
}
export const getAppUserByIdRef: GetAppUserByIdRef;

export function getAppUserById(vars: GetAppUserByIdVariables, options?: ExecuteQueryOptions): QueryPromise<GetAppUserByIdData, GetAppUserByIdVariables>;
export function getAppUserById(dc: DataConnect, vars: GetAppUserByIdVariables, options?: ExecuteQueryOptions): QueryPromise<GetAppUserByIdData, GetAppUserByIdVariables>;

interface GetSpaceListingsRef {
  /* Allow users to create refs without passing in DataConnect */
  (): QueryRef<GetSpaceListingsData, undefined>;
  /* Allow users to pass in custom DataConnect instances */
  (dc: DataConnect): QueryRef<GetSpaceListingsData, undefined>;
  operationName: string;
}
export const getSpaceListingsRef: GetSpaceListingsRef;

export function getSpaceListings(options?: ExecuteQueryOptions): QueryPromise<GetSpaceListingsData, undefined>;
export function getSpaceListings(dc: DataConnect, options?: ExecuteQueryOptions): QueryPromise<GetSpaceListingsData, undefined>;

interface GetSpaceSubdivisionsRef {
  /* Allow users to create refs without passing in DataConnect */
  (): QueryRef<GetSpaceSubdivisionsData, undefined>;
  /* Allow users to pass in custom DataConnect instances */
  (dc: DataConnect): QueryRef<GetSpaceSubdivisionsData, undefined>;
  operationName: string;
}
export const getSpaceSubdivisionsRef: GetSpaceSubdivisionsRef;

export function getSpaceSubdivisions(options?: ExecuteQueryOptions): QueryPromise<GetSpaceSubdivisionsData, undefined>;
export function getSpaceSubdivisions(dc: DataConnect, options?: ExecuteQueryOptions): QueryPromise<GetSpaceSubdivisionsData, undefined>;

interface GetSpaceSubdivisionsBySpaceIdRef {
  /* Allow users to create refs without passing in DataConnect */
  (vars: GetSpaceSubdivisionsBySpaceIdVariables): QueryRef<GetSpaceSubdivisionsBySpaceIdData, GetSpaceSubdivisionsBySpaceIdVariables>;
  /* Allow users to pass in custom DataConnect instances */
  (dc: DataConnect, vars: GetSpaceSubdivisionsBySpaceIdVariables): QueryRef<GetSpaceSubdivisionsBySpaceIdData, GetSpaceSubdivisionsBySpaceIdVariables>;
  operationName: string;
}
export const getSpaceSubdivisionsBySpaceIdRef: GetSpaceSubdivisionsBySpaceIdRef;

export function getSpaceSubdivisionsBySpaceId(vars: GetSpaceSubdivisionsBySpaceIdVariables, options?: ExecuteQueryOptions): QueryPromise<GetSpaceSubdivisionsBySpaceIdData, GetSpaceSubdivisionsBySpaceIdVariables>;
export function getSpaceSubdivisionsBySpaceId(dc: DataConnect, vars: GetSpaceSubdivisionsBySpaceIdVariables, options?: ExecuteQueryOptions): QueryPromise<GetSpaceSubdivisionsBySpaceIdData, GetSpaceSubdivisionsBySpaceIdVariables>;

interface GetBookingRequestsRef {
  /* Allow users to create refs without passing in DataConnect */
  (): QueryRef<GetBookingRequestsData, undefined>;
  /* Allow users to pass in custom DataConnect instances */
  (dc: DataConnect): QueryRef<GetBookingRequestsData, undefined>;
  operationName: string;
}
export const getBookingRequestsRef: GetBookingRequestsRef;

export function getBookingRequests(options?: ExecuteQueryOptions): QueryPromise<GetBookingRequestsData, undefined>;
export function getBookingRequests(dc: DataConnect, options?: ExecuteQueryOptions): QueryPromise<GetBookingRequestsData, undefined>;

interface GetWhishTransactionsRef {
  /* Allow users to create refs without passing in DataConnect */
  (): QueryRef<GetWhishTransactionsData, undefined>;
  /* Allow users to pass in custom DataConnect instances */
  (dc: DataConnect): QueryRef<GetWhishTransactionsData, undefined>;
  operationName: string;
}
export const getWhishTransactionsRef: GetWhishTransactionsRef;

export function getWhishTransactions(options?: ExecuteQueryOptions): QueryPromise<GetWhishTransactionsData, undefined>;
export function getWhishTransactions(dc: DataConnect, options?: ExecuteQueryOptions): QueryPromise<GetWhishTransactionsData, undefined>;

interface GetSuccessfulTransactionsInRangeRef {
  /* Allow users to create refs without passing in DataConnect */
  (vars: GetSuccessfulTransactionsInRangeVariables): QueryRef<GetSuccessfulTransactionsInRangeData, GetSuccessfulTransactionsInRangeVariables>;
  /* Allow users to pass in custom DataConnect instances */
  (dc: DataConnect, vars: GetSuccessfulTransactionsInRangeVariables): QueryRef<GetSuccessfulTransactionsInRangeData, GetSuccessfulTransactionsInRangeVariables>;
  operationName: string;
}
export const getSuccessfulTransactionsInRangeRef: GetSuccessfulTransactionsInRangeRef;

export function getSuccessfulTransactionsInRange(vars: GetSuccessfulTransactionsInRangeVariables, options?: ExecuteQueryOptions): QueryPromise<GetSuccessfulTransactionsInRangeData, GetSuccessfulTransactionsInRangeVariables>;
export function getSuccessfulTransactionsInRange(dc: DataConnect, vars: GetSuccessfulTransactionsInRangeVariables, options?: ExecuteQueryOptions): QueryPromise<GetSuccessfulTransactionsInRangeData, GetSuccessfulTransactionsInRangeVariables>;

interface GetAuditSecurityLogsRef {
  /* Allow users to create refs without passing in DataConnect */
  (): QueryRef<GetAuditSecurityLogsData, undefined>;
  /* Allow users to pass in custom DataConnect instances */
  (dc: DataConnect): QueryRef<GetAuditSecurityLogsData, undefined>;
  operationName: string;
}
export const getAuditSecurityLogsRef: GetAuditSecurityLogsRef;

export function getAuditSecurityLogs(options?: ExecuteQueryOptions): QueryPromise<GetAuditSecurityLogsData, undefined>;
export function getAuditSecurityLogs(dc: DataConnect, options?: ExecuteQueryOptions): QueryPromise<GetAuditSecurityLogsData, undefined>;

interface GetAvatarCampaignsRef {
  /* Allow users to create refs without passing in DataConnect */
  (): QueryRef<GetAvatarCampaignsData, undefined>;
  /* Allow users to pass in custom DataConnect instances */
  (dc: DataConnect): QueryRef<GetAvatarCampaignsData, undefined>;
  operationName: string;
}
export const getAvatarCampaignsRef: GetAvatarCampaignsRef;

export function getAvatarCampaigns(options?: ExecuteQueryOptions): QueryPromise<GetAvatarCampaignsData, undefined>;
export function getAvatarCampaigns(dc: DataConnect, options?: ExecuteQueryOptions): QueryPromise<GetAvatarCampaignsData, undefined>;

interface GetAdminPricingStatesRef {
  /* Allow users to create refs without passing in DataConnect */
  (): QueryRef<GetAdminPricingStatesData, undefined>;
  /* Allow users to pass in custom DataConnect instances */
  (dc: DataConnect): QueryRef<GetAdminPricingStatesData, undefined>;
  operationName: string;
}
export const getAdminPricingStatesRef: GetAdminPricingStatesRef;

export function getAdminPricingStates(options?: ExecuteQueryOptions): QueryPromise<GetAdminPricingStatesData, undefined>;
export function getAdminPricingStates(dc: DataConnect, options?: ExecuteQueryOptions): QueryPromise<GetAdminPricingStatesData, undefined>;

