const { queryRef, executeQuery, validateArgsWithOptions, mutationRef, executeMutation, validateArgs } = require('firebase/data-connect');

const connectorConfig = {
  connector: 'prospace-connector',
  service: 'prospace-dataconnect-medlb',
  location: 'europe-west1'
};
exports.connectorConfig = connectorConfig;

const upsertAppUserRef = (dcOrVars, vars) => {
  const { dc: dcInstance, vars: inputVars} = validateArgs(connectorConfig, dcOrVars, vars, true);
  dcInstance._useGeneratedSdk();
  return mutationRef(dcInstance, 'UpsertAppUser', inputVars);
}
upsertAppUserRef.operationName = 'UpsertAppUser';
exports.upsertAppUserRef = upsertAppUserRef;

exports.upsertAppUser = function upsertAppUser(dcOrVars, vars) {
  const { dc: dcInstance, vars: inputVars } = validateArgs(connectorConfig, dcOrVars, vars, true);
  return executeMutation(upsertAppUserRef(dcInstance, inputVars));
}
;

const upsertSpaceListingRef = (dcOrVars, vars) => {
  const { dc: dcInstance, vars: inputVars} = validateArgs(connectorConfig, dcOrVars, vars, true);
  dcInstance._useGeneratedSdk();
  return mutationRef(dcInstance, 'UpsertSpaceListing', inputVars);
}
upsertSpaceListingRef.operationName = 'UpsertSpaceListing';
exports.upsertSpaceListingRef = upsertSpaceListingRef;

exports.upsertSpaceListing = function upsertSpaceListing(dcOrVars, vars) {
  const { dc: dcInstance, vars: inputVars } = validateArgs(connectorConfig, dcOrVars, vars, true);
  return executeMutation(upsertSpaceListingRef(dcInstance, inputVars));
}
;

const upsertSpaceSubdivisionRef = (dcOrVars, vars) => {
  const { dc: dcInstance, vars: inputVars} = validateArgs(connectorConfig, dcOrVars, vars, true);
  dcInstance._useGeneratedSdk();
  return mutationRef(dcInstance, 'UpsertSpaceSubdivision', inputVars);
}
upsertSpaceSubdivisionRef.operationName = 'UpsertSpaceSubdivision';
exports.upsertSpaceSubdivisionRef = upsertSpaceSubdivisionRef;

exports.upsertSpaceSubdivision = function upsertSpaceSubdivision(dcOrVars, vars) {
  const { dc: dcInstance, vars: inputVars } = validateArgs(connectorConfig, dcOrVars, vars, true);
  return executeMutation(upsertSpaceSubdivisionRef(dcInstance, inputVars));
}
;

const deleteSpaceSubdivisionRef = (dcOrVars, vars) => {
  const { dc: dcInstance, vars: inputVars} = validateArgs(connectorConfig, dcOrVars, vars, true);
  dcInstance._useGeneratedSdk();
  return mutationRef(dcInstance, 'DeleteSpaceSubdivision', inputVars);
}
deleteSpaceSubdivisionRef.operationName = 'DeleteSpaceSubdivision';
exports.deleteSpaceSubdivisionRef = deleteSpaceSubdivisionRef;

exports.deleteSpaceSubdivision = function deleteSpaceSubdivision(dcOrVars, vars) {
  const { dc: dcInstance, vars: inputVars } = validateArgs(connectorConfig, dcOrVars, vars, true);
  return executeMutation(deleteSpaceSubdivisionRef(dcInstance, inputVars));
}
;

const upsertBookingRequestRef = (dcOrVars, vars) => {
  const { dc: dcInstance, vars: inputVars} = validateArgs(connectorConfig, dcOrVars, vars, true);
  dcInstance._useGeneratedSdk();
  return mutationRef(dcInstance, 'UpsertBookingRequest', inputVars);
}
upsertBookingRequestRef.operationName = 'UpsertBookingRequest';
exports.upsertBookingRequestRef = upsertBookingRequestRef;

exports.upsertBookingRequest = function upsertBookingRequest(dcOrVars, vars) {
  const { dc: dcInstance, vars: inputVars } = validateArgs(connectorConfig, dcOrVars, vars, true);
  return executeMutation(upsertBookingRequestRef(dcInstance, inputVars));
}
;

const insertWhishTransactionRef = (dcOrVars, vars) => {
  const { dc: dcInstance, vars: inputVars} = validateArgs(connectorConfig, dcOrVars, vars, true);
  dcInstance._useGeneratedSdk();
  return mutationRef(dcInstance, 'InsertWhishTransaction', inputVars);
}
insertWhishTransactionRef.operationName = 'InsertWhishTransaction';
exports.insertWhishTransactionRef = insertWhishTransactionRef;

exports.insertWhishTransaction = function insertWhishTransaction(dcOrVars, vars) {
  const { dc: dcInstance, vars: inputVars } = validateArgs(connectorConfig, dcOrVars, vars, true);
  return executeMutation(insertWhishTransactionRef(dcInstance, inputVars));
}
;

const upsertWhishTransactionRef = (dcOrVars, vars) => {
  const { dc: dcInstance, vars: inputVars} = validateArgs(connectorConfig, dcOrVars, vars, true);
  dcInstance._useGeneratedSdk();
  return mutationRef(dcInstance, 'UpsertWhishTransaction', inputVars);
}
upsertWhishTransactionRef.operationName = 'UpsertWhishTransaction';
exports.upsertWhishTransactionRef = upsertWhishTransactionRef;

exports.upsertWhishTransaction = function upsertWhishTransaction(dcOrVars, vars) {
  const { dc: dcInstance, vars: inputVars } = validateArgs(connectorConfig, dcOrVars, vars, true);
  return executeMutation(upsertWhishTransactionRef(dcInstance, inputVars));
}
;

const insertAuditSecurityLogRef = (dcOrVars, vars) => {
  const { dc: dcInstance, vars: inputVars} = validateArgs(connectorConfig, dcOrVars, vars, true);
  dcInstance._useGeneratedSdk();
  return mutationRef(dcInstance, 'InsertAuditSecurityLog', inputVars);
}
insertAuditSecurityLogRef.operationName = 'InsertAuditSecurityLog';
exports.insertAuditSecurityLogRef = insertAuditSecurityLogRef;

exports.insertAuditSecurityLog = function insertAuditSecurityLog(dcOrVars, vars) {
  const { dc: dcInstance, vars: inputVars } = validateArgs(connectorConfig, dcOrVars, vars, true);
  return executeMutation(insertAuditSecurityLogRef(dcInstance, inputVars));
}
;

const upsertAvatarCampaignRef = (dcOrVars, vars) => {
  const { dc: dcInstance, vars: inputVars} = validateArgs(connectorConfig, dcOrVars, vars, true);
  dcInstance._useGeneratedSdk();
  return mutationRef(dcInstance, 'UpsertAvatarCampaign', inputVars);
}
upsertAvatarCampaignRef.operationName = 'UpsertAvatarCampaign';
exports.upsertAvatarCampaignRef = upsertAvatarCampaignRef;

exports.upsertAvatarCampaign = function upsertAvatarCampaign(dcOrVars, vars) {
  const { dc: dcInstance, vars: inputVars } = validateArgs(connectorConfig, dcOrVars, vars, true);
  return executeMutation(upsertAvatarCampaignRef(dcInstance, inputVars));
}
;

const upsertAdminPricingStateRef = (dcOrVars, vars) => {
  const { dc: dcInstance, vars: inputVars} = validateArgs(connectorConfig, dcOrVars, vars, true);
  dcInstance._useGeneratedSdk();
  return mutationRef(dcInstance, 'UpsertAdminPricingState', inputVars);
}
upsertAdminPricingStateRef.operationName = 'UpsertAdminPricingState';
exports.upsertAdminPricingStateRef = upsertAdminPricingStateRef;

exports.upsertAdminPricingState = function upsertAdminPricingState(dcOrVars, vars) {
  const { dc: dcInstance, vars: inputVars } = validateArgs(connectorConfig, dcOrVars, vars, true);
  return executeMutation(upsertAdminPricingStateRef(dcInstance, inputVars));
}
;

const deleteSpaceListingRef = (dcOrVars, vars) => {
  const { dc: dcInstance, vars: inputVars} = validateArgs(connectorConfig, dcOrVars, vars, true);
  dcInstance._useGeneratedSdk();
  return mutationRef(dcInstance, 'DeleteSpaceListing', inputVars);
}
deleteSpaceListingRef.operationName = 'DeleteSpaceListing';
exports.deleteSpaceListingRef = deleteSpaceListingRef;

exports.deleteSpaceListing = function deleteSpaceListing(dcOrVars, vars) {
  const { dc: dcInstance, vars: inputVars } = validateArgs(connectorConfig, dcOrVars, vars, true);
  return executeMutation(deleteSpaceListingRef(dcInstance, inputVars));
}
;

const deleteBookingRequestRef = (dcOrVars, vars) => {
  const { dc: dcInstance, vars: inputVars} = validateArgs(connectorConfig, dcOrVars, vars, true);
  dcInstance._useGeneratedSdk();
  return mutationRef(dcInstance, 'DeleteBookingRequest', inputVars);
}
deleteBookingRequestRef.operationName = 'DeleteBookingRequest';
exports.deleteBookingRequestRef = deleteBookingRequestRef;

exports.deleteBookingRequest = function deleteBookingRequest(dcOrVars, vars) {
  const { dc: dcInstance, vars: inputVars } = validateArgs(connectorConfig, dcOrVars, vars, true);
  return executeMutation(deleteBookingRequestRef(dcInstance, inputVars));
}
;

const getAppUsersRef = (dc) => {
  const { dc: dcInstance} = validateArgs(connectorConfig, dc, undefined);
  dcInstance._useGeneratedSdk();
  return queryRef(dcInstance, 'GetAppUsers');
}
getAppUsersRef.operationName = 'GetAppUsers';
exports.getAppUsersRef = getAppUsersRef;

exports.getAppUsers = function getAppUsers(dcOrOptions, options) {
  
  const { dc: dcInstance, vars: inputVars, options: inputOpts } = validateArgsWithOptions(connectorConfig, dcOrOptions, options, undefined,false, false);
  return executeQuery(getAppUsersRef(dcInstance, inputVars), inputOpts && { fetchPolicy: inputOpts.fetchPolicy });
}
;

const getAppUserByIdRef = (dcOrVars, vars) => {
  const { dc: dcInstance, vars: inputVars} = validateArgs(connectorConfig, dcOrVars, vars, true);
  dcInstance._useGeneratedSdk();
  return queryRef(dcInstance, 'GetAppUserById', inputVars);
}
getAppUserByIdRef.operationName = 'GetAppUserById';
exports.getAppUserByIdRef = getAppUserByIdRef;

exports.getAppUserById = function getAppUserById(dcOrVars, varsOrOptions, options) {
  
  const { dc: dcInstance, vars: inputVars, options: inputOpts } = validateArgsWithOptions(connectorConfig, dcOrVars, varsOrOptions, options, true, true);
  return executeQuery(getAppUserByIdRef(dcInstance, inputVars), inputOpts && { fetchPolicy: inputOpts.fetchPolicy });
}
;

const getSpaceListingsRef = (dc) => {
  const { dc: dcInstance} = validateArgs(connectorConfig, dc, undefined);
  dcInstance._useGeneratedSdk();
  return queryRef(dcInstance, 'GetSpaceListings');
}
getSpaceListingsRef.operationName = 'GetSpaceListings';
exports.getSpaceListingsRef = getSpaceListingsRef;

exports.getSpaceListings = function getSpaceListings(dcOrOptions, options) {
  
  const { dc: dcInstance, vars: inputVars, options: inputOpts } = validateArgsWithOptions(connectorConfig, dcOrOptions, options, undefined,false, false);
  return executeQuery(getSpaceListingsRef(dcInstance, inputVars), inputOpts && { fetchPolicy: inputOpts.fetchPolicy });
}
;

const getSpaceSubdivisionsRef = (dc) => {
  const { dc: dcInstance} = validateArgs(connectorConfig, dc, undefined);
  dcInstance._useGeneratedSdk();
  return queryRef(dcInstance, 'GetSpaceSubdivisions');
}
getSpaceSubdivisionsRef.operationName = 'GetSpaceSubdivisions';
exports.getSpaceSubdivisionsRef = getSpaceSubdivisionsRef;

exports.getSpaceSubdivisions = function getSpaceSubdivisions(dcOrOptions, options) {
  
  const { dc: dcInstance, vars: inputVars, options: inputOpts } = validateArgsWithOptions(connectorConfig, dcOrOptions, options, undefined,false, false);
  return executeQuery(getSpaceSubdivisionsRef(dcInstance, inputVars), inputOpts && { fetchPolicy: inputOpts.fetchPolicy });
}
;

const getSpaceSubdivisionsBySpaceIdRef = (dcOrVars, vars) => {
  const { dc: dcInstance, vars: inputVars} = validateArgs(connectorConfig, dcOrVars, vars, true);
  dcInstance._useGeneratedSdk();
  return queryRef(dcInstance, 'GetSpaceSubdivisionsBySpaceId', inputVars);
}
getSpaceSubdivisionsBySpaceIdRef.operationName = 'GetSpaceSubdivisionsBySpaceId';
exports.getSpaceSubdivisionsBySpaceIdRef = getSpaceSubdivisionsBySpaceIdRef;

exports.getSpaceSubdivisionsBySpaceId = function getSpaceSubdivisionsBySpaceId(dcOrVars, varsOrOptions, options) {
  
  const { dc: dcInstance, vars: inputVars, options: inputOpts } = validateArgsWithOptions(connectorConfig, dcOrVars, varsOrOptions, options, true, true);
  return executeQuery(getSpaceSubdivisionsBySpaceIdRef(dcInstance, inputVars), inputOpts && { fetchPolicy: inputOpts.fetchPolicy });
}
;

const getBookingRequestsRef = (dc) => {
  const { dc: dcInstance} = validateArgs(connectorConfig, dc, undefined);
  dcInstance._useGeneratedSdk();
  return queryRef(dcInstance, 'GetBookingRequests');
}
getBookingRequestsRef.operationName = 'GetBookingRequests';
exports.getBookingRequestsRef = getBookingRequestsRef;

exports.getBookingRequests = function getBookingRequests(dcOrOptions, options) {
  
  const { dc: dcInstance, vars: inputVars, options: inputOpts } = validateArgsWithOptions(connectorConfig, dcOrOptions, options, undefined,false, false);
  return executeQuery(getBookingRequestsRef(dcInstance, inputVars), inputOpts && { fetchPolicy: inputOpts.fetchPolicy });
}
;

const getWhishTransactionsRef = (dc) => {
  const { dc: dcInstance} = validateArgs(connectorConfig, dc, undefined);
  dcInstance._useGeneratedSdk();
  return queryRef(dcInstance, 'GetWhishTransactions');
}
getWhishTransactionsRef.operationName = 'GetWhishTransactions';
exports.getWhishTransactionsRef = getWhishTransactionsRef;

exports.getWhishTransactions = function getWhishTransactions(dcOrOptions, options) {
  
  const { dc: dcInstance, vars: inputVars, options: inputOpts } = validateArgsWithOptions(connectorConfig, dcOrOptions, options, undefined,false, false);
  return executeQuery(getWhishTransactionsRef(dcInstance, inputVars), inputOpts && { fetchPolicy: inputOpts.fetchPolicy });
}
;

const getSuccessfulTransactionsInRangeRef = (dcOrVars, vars) => {
  const { dc: dcInstance, vars: inputVars} = validateArgs(connectorConfig, dcOrVars, vars, true);
  dcInstance._useGeneratedSdk();
  return queryRef(dcInstance, 'GetSuccessfulTransactionsInRange', inputVars);
}
getSuccessfulTransactionsInRangeRef.operationName = 'GetSuccessfulTransactionsInRange';
exports.getSuccessfulTransactionsInRangeRef = getSuccessfulTransactionsInRangeRef;

exports.getSuccessfulTransactionsInRange = function getSuccessfulTransactionsInRange(dcOrVars, varsOrOptions, options) {
  
  const { dc: dcInstance, vars: inputVars, options: inputOpts } = validateArgsWithOptions(connectorConfig, dcOrVars, varsOrOptions, options, true, true);
  return executeQuery(getSuccessfulTransactionsInRangeRef(dcInstance, inputVars), inputOpts && { fetchPolicy: inputOpts.fetchPolicy });
}
;

const getAuditSecurityLogsRef = (dc) => {
  const { dc: dcInstance} = validateArgs(connectorConfig, dc, undefined);
  dcInstance._useGeneratedSdk();
  return queryRef(dcInstance, 'GetAuditSecurityLogs');
}
getAuditSecurityLogsRef.operationName = 'GetAuditSecurityLogs';
exports.getAuditSecurityLogsRef = getAuditSecurityLogsRef;

exports.getAuditSecurityLogs = function getAuditSecurityLogs(dcOrOptions, options) {
  
  const { dc: dcInstance, vars: inputVars, options: inputOpts } = validateArgsWithOptions(connectorConfig, dcOrOptions, options, undefined,false, false);
  return executeQuery(getAuditSecurityLogsRef(dcInstance, inputVars), inputOpts && { fetchPolicy: inputOpts.fetchPolicy });
}
;

const getAvatarCampaignsRef = (dc) => {
  const { dc: dcInstance} = validateArgs(connectorConfig, dc, undefined);
  dcInstance._useGeneratedSdk();
  return queryRef(dcInstance, 'GetAvatarCampaigns');
}
getAvatarCampaignsRef.operationName = 'GetAvatarCampaigns';
exports.getAvatarCampaignsRef = getAvatarCampaignsRef;

exports.getAvatarCampaigns = function getAvatarCampaigns(dcOrOptions, options) {
  
  const { dc: dcInstance, vars: inputVars, options: inputOpts } = validateArgsWithOptions(connectorConfig, dcOrOptions, options, undefined,false, false);
  return executeQuery(getAvatarCampaignsRef(dcInstance, inputVars), inputOpts && { fetchPolicy: inputOpts.fetchPolicy });
}
;

const getAdminPricingStatesRef = (dc) => {
  const { dc: dcInstance} = validateArgs(connectorConfig, dc, undefined);
  dcInstance._useGeneratedSdk();
  return queryRef(dcInstance, 'GetAdminPricingStates');
}
getAdminPricingStatesRef.operationName = 'GetAdminPricingStates';
exports.getAdminPricingStatesRef = getAdminPricingStatesRef;

exports.getAdminPricingStates = function getAdminPricingStates(dcOrOptions, options) {
  
  const { dc: dcInstance, vars: inputVars, options: inputOpts } = validateArgsWithOptions(connectorConfig, dcOrOptions, options, undefined,false, false);
  return executeQuery(getAdminPricingStatesRef(dcInstance, inputVars), inputOpts && { fetchPolicy: inputOpts.fetchPolicy });
}
;
