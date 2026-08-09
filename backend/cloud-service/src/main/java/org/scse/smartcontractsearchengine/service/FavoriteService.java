package org.scse.smartcontractsearchengine.service;

import org.scse.smartcontractsearchengine.exception.FavoriteException;
import org.scse.smartcontractsearchengine.model.vo.ContractCardVo;
import org.scse.smartcontractsearchengine.model.vo.FavoriteFolderVo;

import java.util.List;

public interface FavoriteService {

    String addFavorite(Long userId, Long contractId) throws FavoriteException;

    String addFavorite(Long userId, Long contractId, Long folderId, String newFolderName) throws FavoriteException;

    String removeFavorite(Long userId, Long contractId) throws FavoriteException;

    List<ContractCardVo> getFavoriteContracts(Long userId);

    List<FavoriteFolderVo> getFavoriteFolders(Long userId);

    FavoriteFolderVo createFavoriteFolder(Long userId, String name) throws FavoriteException;

    FavoriteFolderVo renameFavoriteFolder(Long userId, Long folderId, String name) throws FavoriteException;

    void deleteFavoriteFolder(Long userId, Long folderId) throws FavoriteException;

    List<ContractCardVo> getFavoriteContractsByFolder(Long userId, Long folderId) throws FavoriteException;

}
